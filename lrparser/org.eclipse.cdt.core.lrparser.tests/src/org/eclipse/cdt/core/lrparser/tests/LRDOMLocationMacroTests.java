/*******************************************************************************
 * Copyright (c) 2006, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.lrparser.tests;

import junit.framework.TestSuite;

import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.lrparser.c99.C99Language;
import org.eclipse.cdt.core.dom.lrparser.cpp.ISOCPPLanguage;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.ParserLanguage;
import org.eclipse.cdt.core.parser.tests.ast2.DOMLocationMacroTests;
import org.eclipse.cdt.internal.core.parser.ParserException;

@SuppressWarnings("restriction")
public class LRDOMLocationMacroTests extends DOMLocationMacroTests {

	public static TestSuite suite() {
    	return suite(LRDOMLocationMacroTests.class);
    }
	
    public LRDOMLocationMacroTests() {}
	public LRDOMLocationMacroTests(String name) { super(name); }


	@Override
	@SuppressWarnings("unused") 
	protected IASTTranslationUnit parse( String code, ParserLanguage lang, boolean useGNUExtensions, boolean expectNoProblems ) throws ParserException {
		ILanguage language = lang.isCPP() ? getCPPLanguage() : getC99Language();
		ParseHelper.Options options = new ParseHelper.Options().setCheckSyntaxProblems(expectNoProblems).setCheckPreprocessorProblems(expectNoProblems);
   		return ParseHelper.parse(code, language, options);
    }
    
    
	protected ILanguage getC99Language() {
    	return C99Language.getDefault();
    }
	
	protected ILanguage getCPPLanguage() {
		return ISOCPPLanguage.getDefault();
	}
    
    
    /**
     * Tests GCC specific stuff, not applicable at this point
     */
    
	@Override
	public void testStdioBug() throws ParserException {
    	try {
    		super.testStdioBug();
    		fail();
    	}
    	catch(Throwable e) { }
    }
    
}
