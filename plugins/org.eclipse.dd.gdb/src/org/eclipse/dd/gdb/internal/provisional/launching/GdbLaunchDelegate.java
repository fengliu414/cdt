/*******************************************************************************
 * Copyright (c) 2008  QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems   - Initial API and implementation
 * Windriver and Ericsson - Updated for DSF
 *******************************************************************************/
package org.eclipse.dd.gdb.internal.provisional.launching; 

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import org.eclipse.cdt.core.model.ICModelMarker;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.dd.dsf.concurrent.DsfExecutor;
import org.eclipse.dd.dsf.concurrent.Sequence;
import org.eclipse.dd.dsf.concurrent.ThreadSafe;
import org.eclipse.dd.dsf.debug.service.IDsfDebugServicesFactory;
import org.eclipse.dd.dsf.debug.sourcelookup.DsfSourceLookupDirector;
import org.eclipse.dd.dsf.service.DsfSession;
import org.eclipse.dd.gdb.internal.GdbPlugin;
import org.eclipse.dd.gdb.internal.provisional.IGDBLaunchConfigurationConstants;
import org.eclipse.dd.gdb.internal.provisional.service.GdbDebugServicesFactory;
import org.eclipse.dd.gdb.internal.provisional.service.GdbDebugServicesFactoryNS;
import org.eclipse.dd.gdb.internal.provisional.service.command.GDBControl.SessionType;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate2;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
 
/**
 * The shared launch configuration delegate for the DSF/GDB debugger.
 * This delegate supports all configuration types (local, remote, attach, etc)
 */
@ThreadSafe
public class GdbLaunchDelegate extends LaunchConfigurationDelegate 
    implements ILaunchConfigurationDelegate2
{
    public final static String GDB_DEBUG_MODEL_ID = "org.eclipse.dd.gdb"; //$NON-NLS-1$

    private final static String NON_STOP_FIRST_VERSION = "6.8.50"; //$NON-NLS-1$
	private boolean isNonStopSession = false;

	public void launch( ILaunchConfiguration config, String mode, ILaunch launch, IProgressMonitor monitor ) throws CoreException {
		if ( monitor == null ) {
			monitor = new NullProgressMonitor();
		}
		if ( mode.equals( ILaunchManager.DEBUG_MODE ) ) {
			launchDebugger( config, launch, monitor );
		}
	}

	private void launchDebugger( ILaunchConfiguration config, ILaunch launch, IProgressMonitor monitor ) throws CoreException {
		monitor.beginTask("Launching debugger session", 10); //$NON-NLS-1$
		if ( monitor.isCanceled() ) {
			return;
		}

		try {
    		launchDebugSession( config, launch, monitor );
		}
		finally {
			monitor.done();
		}		
	}

	private void launchDebugSession( final ILaunchConfiguration config, ILaunch l, IProgressMonitor monitor ) throws CoreException {
		if ( monitor.isCanceled() ) {
			return;
		}
		
		SessionType sessionType = getSessionType(config);
		boolean attach = getIsAttach(config);
		
        final GdbLaunch launch = (GdbLaunch)l;

        if (sessionType == SessionType.REMOTE) {
            monitor.subTask( "Debugging remote C/C++ application" ); //$NON-NLS-1$    	
        } else {
            monitor.subTask( "Debugging local C/C++ application" ); //$NON-NLS-1$
        }
        
        // First verify we are dealing with a proper project.
        ICProject project = LaunchUtils.verifyCProject(config);
        // Now verify we know the program to debug.
		IPath exePath = LaunchUtils.verifyProgramPath(config, project);
		// Finally, make sure the program is a proper binary.
		LaunchUtils.verifyBinary(config, exePath);
    	
        monitor.worked( 1 );

        String gdbVersion = LaunchUtils.getGDBVersion(config);
        
        // First make sure non-stop is supported, if the user want to use this mode
        if (isNonStopSession && !isNonStopSupported(gdbVersion)) {
            throw new DebugException(new Status(IStatus.ERROR, GdbPlugin.PLUGIN_ID, DebugException.REQUEST_FAILED, "Non-stop mode is only supported starting with GDB " + NON_STOP_FIRST_VERSION, null)); //$NON-NLS-1$        	
        }

        launch.setServiceFactory(newServiceFactory(gdbVersion));

        // Create and invoke the launch sequence to create the debug control and services
        final ServicesLaunchSequence servicesLaunchSequence = 
            new ServicesLaunchSequence(launch.getSession(), launch, exePath, sessionType, attach);
        launch.getSession().getExecutor().execute(servicesLaunchSequence);
        try {
            servicesLaunchSequence.get();
        } catch (InterruptedException e1) {
            throw new DebugException(new Status(IStatus.ERROR, GdbPlugin.PLUGIN_ID, DebugException.INTERNAL_ERROR, "Interrupted Exception in dispatch thread", e1)); //$NON-NLS-1$
        } catch (ExecutionException e1) {
            throw new DebugException(new Status(IStatus.ERROR, GdbPlugin.PLUGIN_ID, DebugException.REQUEST_FAILED, "Error in services launch sequence", e1.getCause())); //$NON-NLS-1$
        }
        
        // The initializeControl method should be called after the GdbControl class has
        // be initialized (in the ServicesLaunchSequence above.)  This is because it is the 
        // GdbControl class that will trigger the launch cleanup through a GDBControl.GDBExitedEvent
        launch.initializeControl();

        // Add the CLI and "inferior" process objects to the launch.
        launch.addCLIProcess("gdb"); //$NON-NLS-1$
        launch.addInferiorProcess(exePath.lastSegment());

        // Create and invoke the final launch sequence to setup GDB
        final Sequence finalLaunchSequence = 
        	getFinalLaunchSequence(launch.getSession().getExecutor(), launch, sessionType, attach);
        launch.getSession().getExecutor().execute(finalLaunchSequence);
        try {
        	finalLaunchSequence.get();
        } catch (InterruptedException e1) {
            throw new DebugException(new Status(IStatus.ERROR, GdbPlugin.PLUGIN_ID, DebugException.INTERNAL_ERROR, "Interrupted Exception in dispatch thread", e1)); //$NON-NLS-1$
        } catch (ExecutionException e1) {
            throw new DebugException(new Status(IStatus.ERROR, GdbPlugin.PLUGIN_ID, DebugException.REQUEST_FAILED, "Error in final launch sequence", e1.getCause())); //$NON-NLS-1$
        }
	}

	/*
	 * This method can be overridden by subclasses to allow to change the final launch sequence without
	 * having to change the entire GdbLaunchDelegate
	 */
	protected Sequence getFinalLaunchSequence(DsfExecutor executor, GdbLaunch launch, SessionType type, boolean attach) {
		return new FinalLaunchSequence(executor, launch, type, attach);
	}
	
	private SessionType getSessionType(ILaunchConfiguration config) {
    	try {
    		String debugMode = config.getAttribute( ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_START_MODE, ICDTLaunchConfigurationConstants.DEBUGGER_MODE_RUN );
    		if (debugMode.equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_RUN)) {
    			return SessionType.LOCAL;
    		} else if (debugMode.equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_ATTACH)) {
    			return SessionType.LOCAL;
    		} else if (debugMode.equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_CORE)) {
    			return SessionType.CORE;
    		} else if (debugMode.equals(IGDBLaunchConfigurationConstants.DEBUGGER_MODE_REMOTE)) {
    			return SessionType.REMOTE;
    		} else if (debugMode.equals(IGDBLaunchConfigurationConstants.DEBUGGER_MODE_REMOTE_ATTACH)) {
    		    return SessionType.REMOTE;
    	    }
    	} catch (CoreException e) {    		
    	}
    	return SessionType.LOCAL;
    }
    
	private boolean isNonStopSession(ILaunchConfiguration config) {
		try {
			boolean nonStopMode = config.getAttribute(IGDBLaunchConfigurationConstants.ATTR_DEBUGGER_NON_STOP,
                    IGDBLaunchConfigurationConstants.DEBUGGER_NON_STOP_DEFAULT);
    		return nonStopMode;
    	} catch (CoreException e) {    		
    	}
    	return false;
    }
    
	private boolean getIsAttach(ILaunchConfiguration config) {
    	try {
    		String debugMode = config.getAttribute( ICDTLaunchConfigurationConstants.ATTR_DEBUGGER_START_MODE, ICDTLaunchConfigurationConstants.DEBUGGER_MODE_RUN );
    		if (debugMode.equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_RUN)) {
    			return false;
    		} else if (debugMode.equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_ATTACH)) {
    			return true;
    		} else if (debugMode.equals(ICDTLaunchConfigurationConstants.DEBUGGER_MODE_CORE)) {
    			return false;
    		} else if (debugMode.equals(IGDBLaunchConfigurationConstants.DEBUGGER_MODE_REMOTE)) {
    			return false;
    		} else if (debugMode.equals(IGDBLaunchConfigurationConstants.DEBUGGER_MODE_REMOTE_ATTACH)) {
    		    return true;
    	    }
    	} catch (CoreException e) {    		
    	}
    	return false;
    }
    

	@Override
    public boolean preLaunchCheck(ILaunchConfiguration config, String mode, IProgressMonitor monitor) throws CoreException {
		// no pre launch check for core file
		if (mode.equals(ILaunchManager.DEBUG_MODE) && getSessionType(config) == SessionType.CORE) return true; 
		
		return super.preLaunchCheck(config, mode, monitor);
	}

    @Override
    public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
        // Need to configure the source locator before creating the launch
        // because once the launch is created and added to launch manager, 
        // the adapters will be created for the whole session, including 
        // the source lookup adapter.
        
		isNonStopSession = isNonStopSession(configuration);

        GdbLaunch launch = new GdbLaunch(configuration, mode, null);
        launch.initialize();
        launch.setSourceLocator(getSourceLocator(configuration, launch.getSession()));
        return launch;
    }

    private ISourceLocator getSourceLocator(ILaunchConfiguration configuration, DsfSession session) throws CoreException {
        DsfSourceLookupDirector locator = new DsfSourceLookupDirector(session);
        String memento = configuration.getAttribute(ILaunchConfiguration.ATTR_SOURCE_LOCATOR_MEMENTO, (String)null);
        if (memento == null) {
            locator.initializeDefaults(configuration);
        } else {
            locator.initializeFromMemento(memento, configuration);
        }
        return locator;
    }
    
	/**
	 * Recursively creates a set of projects referenced by the current project
	 * 
	 * @param proj
	 *            The current project
	 * @param referencedProjSet
	 *            A set of referenced projects
	 * @throws CoreException
	 *             if an error occurs while getting referenced projects from the
	 *             current project
	 */
	private HashSet<IProject> getReferencedProjectSet(IProject proj, HashSet<IProject> referencedProjSet) throws CoreException {
		// The top project is a reference too and it must be added at the top to avoid cycles
		referencedProjSet.add(proj);

		IProject[] projects = proj.getReferencedProjects();
		for (IProject refProject : projects) {
			if (refProject.exists() && !referencedProjSet.contains(refProject)) {
				getReferencedProjectSet(refProject, referencedProjSet);
			}
		}
		return referencedProjSet;
	}
	
	/**
	 * Returns the order list of projects to build before launching.
	 *  Used in buildForLaunch() 
	 */
	@Override
	protected IProject[] getBuildOrder(ILaunchConfiguration configuration, String mode) throws CoreException {
		IProject[] orderedProjects = null;
		ArrayList<IProject> orderedProjList = null;

		ICProject cProject = LaunchUtils.verifyCProject(configuration);
		if (cProject != null) {
			HashSet<IProject> projectSet = getReferencedProjectSet(cProject.getProject(), new HashSet<IProject>());

			String[] orderedNames = ResourcesPlugin.getWorkspace().getDescription().getBuildOrder();
			if (orderedNames != null) {
				//Projects may not be in the build order but should still be built if selected
				ArrayList<IProject> unorderedProjects = new ArrayList<IProject>(projectSet.size());
				unorderedProjects.addAll(projectSet);
				orderedProjList = new ArrayList<IProject>(projectSet.size());

				for (String projectName : orderedNames) {
					for (IProject proj : unorderedProjects) {
						if (proj.getName().equals(projectName)) {
							orderedProjList.add(proj);
							unorderedProjects.remove(proj);
							break;
						}
					}
				}

				// Add any remaining projects to the end of the list
				orderedProjList.addAll(unorderedProjects);

				orderedProjects = orderedProjList.toArray(new IProject[orderedProjList.size()]);
			} else {
				// Try the project prerequisite order then
				IProject[] projects = projectSet.toArray(new IProject[projectSet.size()]);
				orderedProjects = ResourcesPlugin.getWorkspace().computeProjectOrder(projects).projects;
			}
		}
		return orderedProjects;
	}

	/* Used in finalLaunchCheck() */
	@Override
	protected IProject[] getProjectsForProblemSearch(ILaunchConfiguration configuration, String mode) throws CoreException {
		return getBuildOrder(configuration, mode);
	}

	/**
	 * Searches for compile errors in the specified project
	 * Used in finalLaunchCheck() 
	 * @param proj
	 *            The project to search
	 * @return true if compile errors exist, otherwise false
	 */
	@Override
	protected boolean existsProblems(IProject proj) throws CoreException {
		IMarker[] markers = proj.findMarkers(ICModelMarker.C_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
		if (markers.length > 0) {
			for (IMarker marker : markers) {
				Integer severity = (Integer)marker.getAttribute(IMarker.SEVERITY);
				if (severity != null) {
					return severity.intValue() >= IMarker.SEVERITY_ERROR;
				}
			}
		}
		return false;
	}
	
	private boolean isNonStopSupported(String version) {
		if (NON_STOP_FIRST_VERSION.compareTo(version) <= 0) {
			return true;
		}
		return false;
	}
	
	private IDsfDebugServicesFactory newServiceFactory(String version) {

		if (isNonStopSession && isNonStopSupported(version)) {
			return new GdbDebugServicesFactoryNS(version);
		}

		if (version.startsWith("6.6") ||  //$NON-NLS-1$
			version.startsWith("6.7") ||  //$NON-NLS-1$
			version.startsWith("6.8")) {  //$NON-NLS-1$
			return new GdbDebugServicesFactory(version);
		}

		return new GdbDebugServicesFactory(version);
	}
}
