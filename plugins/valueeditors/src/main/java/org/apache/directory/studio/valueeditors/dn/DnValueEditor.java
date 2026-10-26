/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.apache.directory.studio.valueeditors.dn;


import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.studio.ldapbrowser.common.dialogs.DnDialog;
import org.apache.directory.studio.ldapbrowser.common.dialogs.TextDialog;
import org.apache.directory.studio.ldapbrowser.core.model.AttributeHierarchy;
import org.apache.directory.studio.ldapbrowser.core.model.IAttribute;
import org.apache.directory.studio.ldapbrowser.core.model.IBrowserConnection;
import org.apache.directory.studio.ldapbrowser.core.model.IEntry;
import org.apache.directory.studio.ldapbrowser.core.model.IValue;
import org.apache.directory.studio.ldapbrowser.core.utils.CompoundModification;
import org.apache.directory.studio.valueeditors.AbstractDialogStringValueEditor;
import org.eclipse.swt.widgets.Shell;


/**
 * Implementation of IValueEditor for syntax 1.3.6.1.4.1.1466.115.121.1.12
 * (Distinguished Name).
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DnValueEditor extends AbstractDialogStringValueEditor
{

    /**
     * {@inheritDoc}
     *
     * When the value being edited is an empty placeholder (a newly added value),
     * the dialog opens in multi-select mode so the user can pick several DNs at
     * once (e.g. to add multiple members to a group).  If more than one DN is
     * chosen, the additional values are written directly via
     * {@link CompoundModification} and the cell-editor path is bypassed.
     *
     * For existing (non-empty) values the dialog opens in the original
     * single-select mode.
     */
    protected boolean openDialog( Shell shell )
    {
        Object value = getValue();

        if ( value instanceof DnValueEditorRawValueWrapper )
        {
            DnValueEditorRawValueWrapper wrapper = ( DnValueEditorRawValueWrapper ) value;

            // --- Multi-select mode for new (empty) values ---
            if ( wrapper.ivalue != null && wrapper.ivalue.isEmpty() )
            {
                DnDialog dialog = new DnDialog( shell,
                    Messages.getString( "DnValueEditor.DNEditor" ), null, wrapper.connection, new Dn[0] ); //$NON-NLS-1$

                if ( dialog.open() != TextDialog.OK )
                {
                    return false;
                }

                Dn[] selectedDns = dialog.getDns();
                if ( selectedDns.length == 0 )
                {
                    return false;
                }

                if ( selectedDns.length == 1 )
                {
                    // Single selection – use the normal cell-editor path
                    setValue( selectedDns[0].getName() );
                    return true;
                }

                // Multiple selections: commit everything directly so that each DN
                // becomes its own attribute value.
                IValue original = wrapper.ivalue;
                IAttribute attribute = original.getAttribute();
                IEntry entry = attribute.getEntry();
                String attrDesc = attribute.getDescription();

                CompoundModification modification = new CompoundModification();
                // Replace the empty placeholder with the first selected DN.
                modification.modifyValue( original, selectedDns[0].getName() );
                // Append the remaining DNs as additional values.
                for ( int i = 1; i < selectedDns.length; i++ )
                {
                    modification.createValue( entry, attrDesc, selectedDns[i].getName() );
                }
                // We have already committed the changes; tell the cell editor to cancel
                // so it does not try to set a value a second time.
                return false;
            }

            // --- Single-select mode for existing values (original behaviour) ---
            Dn dn;
            try
            {
                dn = wrapper.dn != null ? new Dn( wrapper.dn ) : null;
            }
            catch ( LdapInvalidDnException e )
            {
                dn = null;
            }
            DnDialog dialog = new DnDialog( shell,
                Messages.getString( "DnValueEditor.DNEditor" ), null, wrapper.connection, dn ); //$NON-NLS-1$
            if ( dialog.open() == TextDialog.OK && dialog.getDn() != null )
            {
                setValue( dialog.getDn().getName() );
                return true;
            }
        }
        return false;
    }


    /**
     * {@inheritDoc}
     *
     * Returns a DnValueEditorRawValueWrapper with the connection of
     * the attribute hierarchy and a null Dn if there are no values
     * in attributeHierarchy.
     *
     * Returns a DnValueEditorRawValueWrapper with the connection of
     * the attribute hierarchy and a Dn if there is one value
     * in attributeHierarchy.
     */
    public Object getRawValue( AttributeHierarchy attributeHierarchy )
    {
        if ( attributeHierarchy == null )
        {
            return null;
        }
        else if ( attributeHierarchy.size() == 1 && attributeHierarchy.getAttribute().getValueSize() == 0 )
        {
            IBrowserConnection connection = attributeHierarchy.getAttribute().getEntry().getBrowserConnection();
            return new DnValueEditorRawValueWrapper( connection, null );
        }
        else if ( attributeHierarchy.size() == 1 && attributeHierarchy.getAttribute().getValueSize() == 1 )
        {
            IBrowserConnection connection = attributeHierarchy.getAttribute().getEntry().getBrowserConnection();
            return new DnValueEditorRawValueWrapper( connection, getDisplayValue( attributeHierarchy ) );
        }
        else
        {
            return null;
        }
    }


    /**
     * {@inheritDoc}
     *
     * Returns a DnValueEditorRawValueWrapper with the connection of
     * the value and a Dn built from the given value.  The IValue itself is
     * stored in the wrapper so that {@link #openDialog} can detect whether
     * the value is an empty placeholder and act accordingly.
     */
    public Object getRawValue( IValue value )
    {
        Object o = super.getRawValue( value );
        if ( o instanceof String )
        {
            IBrowserConnection connection = value.getAttribute().getEntry().getBrowserConnection();
            return new DnValueEditorRawValueWrapper( connection, ( String ) o, value );
        }

        return null;
    }

    /**
     * The DnValueEditorRawValueWrapper is used to pass contextual
     * information to the opened DnDialog.
     *
     * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
     */
    private class DnValueEditorRawValueWrapper
    {
        /** The connection, used in DnDialog to browse for an entry */
        private IBrowserConnection connection;

        /** The Dn, used as initial value in DnDialog */
        private String dn;

        /**
         * The IValue being edited.  Set when the wrapper originates from the
         * cell-editor path ({@link #getRawValue(IValue)}); null when it comes
         * from the read-only display path ({@link #getRawValue(AttributeHierarchy)}).
         */
        private IValue ivalue;


        /**
         * Creates a new instance of DnValueEditorRawValueWrapper (display path).
         *
         * @param connection the connection
         * @param dn the Dn
         */
        private DnValueEditorRawValueWrapper( IBrowserConnection connection, String dn )
        {
            this( connection, dn, null );
        }


        /**
         * Creates a new instance of DnValueEditorRawValueWrapper (cell-editor path).
         *
         * @param connection the connection
         * @param dn the Dn
         * @param ivalue the IValue being edited
         */
        private DnValueEditorRawValueWrapper( IBrowserConnection connection, String dn, IValue ivalue )
        {
            this.connection = connection;
            this.dn = dn;
            this.ivalue = ivalue;
        }


        /**
         * {@inheritDoc}
         */
        public String toString()
        {
            return dn == null ? "" : dn; //$NON-NLS-1$
        }

    }

}
