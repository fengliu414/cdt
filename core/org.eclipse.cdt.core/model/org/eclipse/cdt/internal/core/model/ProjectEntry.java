/**********************************************************************
 * Created on Mar 25, 2003
 *
 * Copyright (c) 2002,2003 QNX Software Systems Ltd. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * QNX Software Systems - Initial API and implementation
***********************************************************************/
package org.eclipse.cdt.internal.core.model;

import org.eclipse.cdt.core.model.IProjectEntry;
import org.eclipse.core.runtime.IPath;


public class ProjectEntry extends CPathEntry implements IProjectEntry {

	IPath projectPath;

	public ProjectEntry(IPath projectPath, boolean isExported) {
		super(IProjectEntry.CDT_PROJECT, isExported);
		this.projectPath = projectPath;
	}

	/**
	 * Returns the absolute path relative to the workspace root.
	 * @return IPath
	 */
	public IPath getProjectPath() {
		return projectPath;
	}

}
