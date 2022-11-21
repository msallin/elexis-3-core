/*******************************************************************************
 * Copyright (c) 2006-2022, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 *******************************************************************************/

package ch.elexis.core.ui.text;

import java.io.InputStream;

import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.swt.widgets.Composite;

import ch.elexis.core.data.interfaces.text.ReplaceCallback;
import ch.elexis.core.ui.views.textsystem.TextTemplateView;
import ch.elexis.data.Brief;

/**
 * Contract for embedding a text plugin Warning: Preliminary interface
 * 
 */
public interface ITextPlugin2 extends ITextPlugin {

	/**
	 * create a document from a byte array. The array contains the document in the
	 * (arbitrary) specific format of the text component. Prefarably, but not
	 * necessarily OpenDocumentFormat (odf)
	 * 
	 * @param bs         the byte array with the document in a fotmat the compnent
	 *                   can interpret
	 * @param asTemplate tru if the byte array is a template.
	 * @return true on success
	 */
	public boolean loadFromBrief(Brief brief, boolean adTemplate);

}
