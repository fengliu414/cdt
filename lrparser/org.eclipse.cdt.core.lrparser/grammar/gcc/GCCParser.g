-----------------------------------------------------------------------------------
-- Copyright (c) 2008, 2009 IBM Corporation and others.
-- This program and the accompanying materials
-- are made available under the terms of the Eclipse Public License 2.0
-- which accompanies this distribution, and is available at
-- https://www.eclipse.org/legal/epl-2.0/
--
-- SPDX-License-Identifier: EPL-2.0
--
-- Contributors:
--     IBM Corporation - initial API and implementation
-----------------------------------------------------------------------------------

%options la=2
%options package=org.eclipse.cdt.internal.core.dom.lrparser.gcc
%options template=LRParserTemplate.g


-- This file is needed because LPG won't allow redefinition of the 
-- start symbol, so C99Grammar.g cannot define a start symbol.

$Import
	GCCGrammar.g
$End

$Start
    translation_unit
$End