/**********************************************************************
 * Copyright (c) 2002-2004 IBM Canada and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * IBM Rational Software - Initial API and implementation */
package org.eclipse.cdt.core.dom.ast;

/**
 * ASM Statement as a Declaration.
 * 
 * @author jcamelon
 */
public interface IASTASMDeclaration extends IASTDeclaration {

	/**
	 * Get the assembly value.
	 * 
	 * @return
	 */
	public String getAssembly();

	/**
	 * Set the assembly value.
	 * 
	 * @param assembly
	 */
	public void setAssembly(String assembly);
}
