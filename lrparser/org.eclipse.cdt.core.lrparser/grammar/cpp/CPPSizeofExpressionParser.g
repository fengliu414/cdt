-----------------------------------------------------------------------------------
-- Copyright (c) 2006, 2009 IBM Corporation and others.
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
%options package=org.eclipse.cdt.internal.core.dom.lrparser.cpp
%options template=LRSecondaryParserTemplate.g


$Import
	CPPGrammar.g
$DropRules

	unary_expression
	    ::= 'sizeof' '(' type_id ')'
	    
	postfix_expression
        ::= 'typeid' '(' type_id ')'
    
$End

$Define
    $ast_class /. IASTExpression ./
$End

$Start
    no_sizeof_type_id_start
$End

$Rules 

	no_sizeof_type_id_start
	    ::= expression
	      | ERROR_TOKEN
	          /. $Build  consumeEmpty();  $EndBuild ./
          
$End