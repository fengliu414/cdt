package org.eclipse.cdt.core.model;

/**********************************************************************
 * Copyright (c) 2002,2003 Rational Software Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * Rational Software - Initial API and implementation
***********************************************************************/

public interface IDeclaration extends ICElement, ISourceManipulation, ISourceReference {

	/**
	 * 
	 * @return
	 * @throws CModelException
	 */
	boolean isStatic() throws CModelException;
	
	/**
	 * 
	 * @return
	 * @throws CModelException
	 */
	boolean isConst() throws CModelException;
	
	/**
	 * 
	 * @return
	 * @throws CModelException
	 */
	boolean isVolatile() throws CModelException;	
}
