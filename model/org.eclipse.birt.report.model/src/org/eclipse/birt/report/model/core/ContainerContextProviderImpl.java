/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.model.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.birt.report.model.api.DesignElementHandle;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.command.ContentException;
import org.eclipse.birt.report.model.api.core.IModuleModel;
import org.eclipse.birt.report.model.api.elements.ReportDesignConstants;
import org.eclipse.birt.report.model.api.metadata.IElementDefn;
import org.eclipse.birt.report.model.api.metadata.IPredefinedStyle;
import org.eclipse.birt.report.model.api.util.StringUtil;
import org.eclipse.birt.report.model.elements.Library;
import org.eclipse.birt.report.model.elements.ListingElement;
import org.eclipse.birt.report.model.elements.MasterPage;
import org.eclipse.birt.report.model.elements.ReportItemTheme;
import org.eclipse.birt.report.model.elements.TableItem;
import org.eclipse.birt.report.model.elements.TemplateElement;
import org.eclipse.birt.report.model.elements.interfaces.IListingElementModel;
import org.eclipse.birt.report.model.elements.interfaces.ITableItemModel;
import org.eclipse.birt.report.model.metadata.MetaDataDictionary;
import org.eclipse.birt.report.model.util.ContentExceptionFactory;
import org.eclipse.birt.report.model.util.ContentIterator;

/**
 * 
 */
class ContainerContextProviderImpl
{

	/**
	 * The container element.
	 */
	protected ContainerContext focus = null;

	/**
	 * @param containerInfo
	 */
	public ContainerContextProviderImpl( ContainerContext containerInfo )
	{
		if ( containerInfo == null )
			throw new IllegalArgumentException(
					"The containerInfo of this context should not be null" ); //$NON-NLS-1$
		this.focus = containerInfo;
	}

	// refactor codes

	/**
	 * Determines if the slot can contain an element with the type of
	 * <code>type</code>.
	 * 
	 * @param module
	 * 
	 * @param propName
	 *            the slot id
	 * @param type
	 *            the name of the element type, like "Table", "List", etc.
	 * @return <code>true</code> if the slot can contain the an element with
	 *         <code>type</code> type, otherwise <code>false</code>.
	 * 
	 * @see #canContain(int, DesignElementHandle)
	 */

	public final boolean canContain( Module module, String type )
	{
		if ( type == null )
			return false;

		return canContain( module, MetaDataDictionary.getInstance( )
				.getElement( type ) );
	}

	/**
	 * Determines if the slot can contain a given element.
	 * 
	 * @param module
	 *            the module
	 * @param element
	 *            the element to insert
	 * @return a list containing exceptions.
	 */

	public final boolean canContain( Module module, DesignElement element )
	{
		if ( module != null && module.isReadOnly( ) )
			return false;

		List<SemanticException> errors = checkContainmentContext( module,
				element );
		if ( !errors.isEmpty( ) )
			return false;

		return true;
	}

	/**
	 * Determines if the current element can contain an element with the
	 * definition of <code>elementType</code> on context containment.
	 * 
	 * @param module
	 *            the module
	 * @param defn
	 *            the definition of the element
	 * @return <code>true</code> if the slot can contain the an element,
	 *         otherwise <code>false</code>.
	 */

	public boolean canContain( Module module, IElementDefn defn )
	{
		if ( defn == null || ( module != null && module.isReadOnly( ) ) )
			return false;

		boolean retValue = canContainInRom( defn );
		if ( !retValue )
			return false;

		// if the root of element is included by report/library. Do not
		// allow
		// drop.

		if ( focus.getElement( ).isRootIncludedByModule( ) )
			return false;

		if ( !canContainTemplateElement( module, defn ) )
			return false;

		// Can not change structure of child element or a virtual element(
		// inside the child ).

		if ( focus.getElement( ).isVirtualElement( )
				|| focus.getElement( ).getExtendsName( ) != null )
			return false;

		// A summany table cannot contains any detail rows
		if ( focus.getElement( ) instanceof TableItem
				&& focus.getElement( ).getBooleanProperty( module,
						ITableItemModel.IS_SUMMARY_TABLE_PROP )
				&& focus.containerSlotID == IListingElementModel.DETAIL_SLOT )
			return false;

		// special cases check table header containment.

		ContainerContext containerInfo = this.focus;
		while ( containerInfo != null )
		{
			DesignElement container = containerInfo.getElement( );
			if ( container instanceof ListingElement
					|| container instanceof MasterPage )
			{
				List<SemanticException> errors = container.checkContent(
						module, this.focus, defn );
				return errors.isEmpty( );
			}

			containerInfo = container.getContainerInfo( );
		}

		return retValue;
	}

	/**
	 * Determines if the slot can contain a given element.
	 * 
	 * @param module
	 *            the module
	 * @param element
	 *            the element to insert
	 * @return a list containing exceptions.
	 */

	public List<SemanticException> checkContainmentContext( Module module,
			DesignElement element )
	{
		if ( element == null )
			return Collections.emptyList( );

		boolean retValue = canContainInRom( element.getDefn( ) );
		ContentException e = ContentExceptionFactory.createContentException(
				focus, element,
				ContentException.DESIGN_EXCEPTION_INVALID_CONTEXT_CONTAINMENT );

		List<SemanticException> errors = new ArrayList<SemanticException>( );
		if ( !retValue )
		{
			errors.add( e );
			return errors;
		}

		// if this element can not be contained in the module, return false;
		// such as, template elements can not be contained in the libraries,
		// so either a template table or a real table with a template image
		// in one cell of it can never be contained in a libraries

		if ( !canContainTemplateElement( module, element ) )
		{
			errors.add( e );
			return errors;
		}

		// if the root of element is included by report/library. Do not
		// allow
		// drop.

		if ( focus.getElement( ).isRootIncludedByModule( ) )
		{
			errors.add( e );
			return errors;
		}

		// Can not change the structure of child element or a virtual
		// element(
		// inside the child ).

		if ( focus.getElement( ).isVirtualElement( )
				|| focus.getElement( ).getExtendsName( ) != null )
		{
			errors.add( e );
			return errors;
		}

		// A summany table cannot contains any detail rows
		if ( focus.getElement( ) instanceof TableItem
				&& focus.getElement( ).getBooleanProperty( module,
						ITableItemModel.IS_SUMMARY_TABLE_PROP )
				&& focus.containerSlotID == IListingElementModel.DETAIL_SLOT )
		{
			errors.add( e );
			return errors;
		}

		if ( focus.getElement( ) instanceof ReportItemTheme )
		{
			ReportItemTheme theme = (ReportItemTheme) focus.getElement( );
			String type = theme.getType( theme.getRoot( ) );
			IPredefinedStyle style = MetaDataDictionary.getInstance( )
					.getPredefinedStyle( element.getName( ) );
			if ( StringUtil.isBlank( type ) || style == null
					|| !type.equals( style.getType( ) ) )
			{
				errors.add( e );
				return errors;
			}

		}

		// special cases check table header containment.

		ContainerContext containerInfor = focus;
		// DesignElement tmpContainer = this.focus;
		while ( containerInfor != null )
		{
			DesignElement container = containerInfor.getElement( );
			if ( container == element )
			{
				errors.add( e );
				return errors;
			}

			if ( container instanceof ListingElement
					|| container instanceof MasterPage )
			{
				errors = container.checkContent( module, this.focus, element );

				return errors;
			}
			containerInfor = container.getContainerInfo( );
		}

		return Collections.emptyList( );
	}

	/**
	 * Checks whether a type of elements can reside in the given slot of the
	 * current element. Besides the type check, it also checks the cardinality
	 * of this slot.
	 * 
	 * @param slotId
	 *            the slot id of the current element
	 * @param defn
	 *            the element definition
	 * 
	 * @return <code>true</code> if elements with the definition
	 *         <code>definition</code> can reside in the given slot. Otherwise
	 *         <code>false</code>.
	 */

	private boolean canContainInRom( IElementDefn defn )
	{
		if ( !focus.canContainInRom( defn ) )
			return false;

		// if the canContain is check for create template, then jump the
		// slot
		// count check for the operation won't change the content count, it
		// is a
		// replace operation.

		String name = defn.getName( );
		if ( ReportDesignConstants.TEMPLATE_DATA_SET.equals( name )
				|| ReportDesignConstants.TEMPLATE_REPORT_ITEM.equals( name )
				|| ReportDesignConstants.TEMPLATE_ELEMENT.equals( name ) )
			return true;

		if ( focus.getContentCount( focus.getElement( ).getRoot( ) ) > 0
				&& !focus.isContainerMultipleCardinality( ) )
			return false;

		return true;

	}

	/**
	 * Checks whether the element to insert can reside in the given module.
	 * 
	 * @param module
	 *            the root module of the element to add
	 * @param slotID
	 *            the slot ID to insert
	 * @param element
	 *            the element to insert
	 * @return false if the module is a library and the element to insert is a
	 *         template element or its content is a template element; or the
	 *         container is report design and slot is component slot and the
	 *         element to insert is a template element or its content is a
	 *         template element; otherwise true
	 */

	private boolean canContainTemplateElement( Module module,
			DesignElement element )
	{
		// if this element is a kind of template element or any its content
		// is a
		// kind of template element, return false

		IElementDefn defn = MetaDataDictionary.getInstance( ).getElement(
				ReportDesignConstants.TEMPLATE_ELEMENT );

		if ( element instanceof TemplateElement )
			return canContainTemplateElement( module, defn );

		ContentIterator contents = new ContentIterator( module, element );
		while ( contents.hasNext( ) )
		{
			DesignElement content = contents.next( );
			if ( content instanceof TemplateElement )
				return canContainTemplateElement( module, defn );
		}

		return true;
	}

	/**
	 * Checks whether the element to insert can reside in the given module.
	 * 
	 * @param module
	 *            the root module of the element to add
	 * @param slotID
	 *            the slot ID to insert
	 * @param defn
	 *            the definition of element to insert
	 * @return false if the module is a library and the element to insert is a
	 *         template element or its content is a template element; or the
	 *         container is report design and slot is component slot and the
	 *         element to insert is a template element or its content is a
	 *         template element; otherwise true
	 */

	private boolean canContainTemplateElement( Module module, IElementDefn defn )
	{
		// if this element is a kind of template element or any its content
		// is a
		// kind of template element, return false

		if ( defn != null
				&& defn.isKindOf( MetaDataDictionary.getInstance( ).getElement(
						ReportDesignConstants.TEMPLATE_ELEMENT ) ) )
		{
			// components in the design/library cannot contain template
			// elements.

			ContainerContext containerInfo = focus;
			while ( containerInfo != null )
			{
				DesignElement container = containerInfo.getElement( );
				if ( ( container instanceof Module && containerInfo.getSlotID( ) == IModuleModel.COMPONENT_SLOT )
						|| container instanceof Library )
					return false;
				containerInfo = container.getContainerInfo( );
			}

			if ( module instanceof Library )
				return false;
		}

		return true;
	}
}