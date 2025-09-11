/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.views.verticaltab;

import jakarta.annotation.Nullable;
import org.eclipse.swt.graphics.GC;
import org.eclipse.ui.*;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceContainerProvider;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.ui.UITextUtils;
import org.jkiss.dbeaver.ui.actions.AbstractDataSourceHandler;
import org.jkiss.dbeaver.ui.actions.datasource.DataSourceToolbarUtils;
import org.jkiss.dbeaver.ui.editors.DatabaseLazyEditorInput;
import org.jkiss.dbeaver.ui.editors.IDatabaseEditorInput;
import org.jkiss.dbeaver.ui.editors.entity.EntityEditor;

import java.util.Objects;
import java.util.function.Supplier;

public record Database(Datasource datasource, CatalogAndSchema catalogAndSchema) {
    private static final String NA = "?";

    private static String read(Supplier<String> read) {
        try {
            String s = read.get();
            return Objects.requireNonNullElse(s, NA);
        } catch (Exception e) {
            return NA;
        }
    }

    public static boolean sameDatasource(Database self, Database other) {
        if (other == null || self == null) {
            return false;
        }

        return self.datasource == other.datasource;
    }

    public static boolean sameSchema(Database self, Database other) {
        if (other == null || self == null) {
            return false;
        }

        return sameDatasource(self, other) && Objects.equals(self.catalogAndSchema, other.catalogAndSchema);
    }

    public static Database from(IEditorReference editorReference, @Nullable DBPDataSourceContainer dbpDataSource) {
        IEditorPart editorPart = editorReference.getEditor(false);

        DBPDataSourceContainer dataSource = dbpDataSource;
        if ((editorReference instanceof DBPDataSourceContainerProvider dscp)) {
            dataSource = dscp.getDataSourceContainer();
        }

        IWorkbenchWindow workbenchWindow = editorReference.getPage().getWorkbenchWindow();

        return new Database(Datasource.from(dataSource, workbenchWindow), CatalogAndSchema.from(editorPart));
    }

    record Datasource(String id, String name, String user, String ip, String port) implements TextButton.TextObj {
        public static Datasource from(DBPDataSourceContainer dataSource, IWorkbenchWindow workbenchWindow) {
            try {
                DBPConnectionConfiguration conf = dataSource.getConnectionConfiguration();
                String connectionName = dataSource.getName();
                if (workbenchWindow != null) {
                    GC gc = new GC(workbenchWindow.getShell());
                    try {
                        connectionName = UITextUtils.getShortText(gc, connectionName, 200);
                    } finally {
                        gc.dispose();
                    }
                }

                return new Datasource(
                        read(dataSource::getId),
                        connectionName,
                        read(conf::getUserName),
                        read(conf::getHostName),
                        read(conf::getHostPort));
            } catch (Exception ex) {
                Log.getLog(Datasource.class.getCanonicalName()).error("unable to build datasource: " + ex.getMessage());
            }
            return new Datasource(null, null, null, null, null);
        }

        @Override
        public String text() {
            return ip != null ? "%s@%s:%s".formatted(user, ip, port) : this.name;
        }
    }

    record CatalogAndSchema(String catalog, String schema) implements TextButton.TextObj {
        private static CatalogAndSchema from(IEditorPart activeEditor) {
            try {
                IEditorInput editorInput = activeEditor.getEditorInput();
                if (editorInput instanceof IDatabaseEditorInput) {
                    if (editorInput instanceof DatabaseLazyEditorInput) {
                        activeEditor.addPropertyListener(new IPropertyListener() {
                            @Override
                            public void propertyChanged(Object source, int propId) {
                                if (EntityEditor.PROP_TITLE == propId) {
                                    DataSourceToolbarUtils.updateCommandsUI();
                                    activeEditor.removePropertyListener(this);
                                }
                            }
                        });
                    }
                    DBCExecutionContext executionContext = ((IDatabaseEditorInput) editorInput).getExecutionContext();
                    if (executionContext != null) {
                        DBSObject schemaObject = DBUtils.getSelectedObject(executionContext);
                        if (schemaObject != null) {
                            DBSObject schemaParent = schemaObject.getParentObject();
                            if (schemaParent instanceof DBSObjectContainer
                                    && !(schemaParent instanceof DBPDataSource)) {
                                return new CatalogAndSchema(schemaParent.getName(), schemaObject.getName());
                            } else {
                                return new CatalogAndSchema(null, schemaObject.getName());
                            }
                        }
                    }
                } else {
                    DBCExecutionContext executionContext =
                            AbstractDataSourceHandler.getExecutionContextFromPart(activeEditor);
                    DBCExecutionContextDefaults<?, ?> contextDefaults = null;
                    if (executionContext != null) {
                        contextDefaults = executionContext.getContextDefaults();
                    }
                    if (contextDefaults != null) {
                        DBSCatalog defaultCatalog = contextDefaults.getDefaultCatalog();
                        DBSSchema defaultSchema = contextDefaults.getDefaultSchema();
                        if (defaultCatalog != null
                                && (defaultSchema != null || contextDefaults.supportsSchemaChange())) {
                            if (defaultSchema == null) {
                                return new CatalogAndSchema(null, null);
                            } else {
                                return new CatalogAndSchema(defaultCatalog.getName(), defaultSchema.getName());
                            }
                        } else if (defaultCatalog != null) {
                            return new CatalogAndSchema(defaultCatalog.getName(), null);
                        } else if (defaultSchema != null) {
                            return new CatalogAndSchema(null, defaultSchema.getName());
                        }
                    }
                }
            } catch (Exception e) {
                Log.getLog(Datasource.class.getCanonicalName()).error("unable to build catalog: " + e.getMessage());
            }
            return new CatalogAndSchema(null, null);
        }

        @Override
        public String toString() {
            return text();
        }

        @Override
        public String text() {
            return catalog != null ? (schema != null ? "%s@%s".formatted(schema, catalog) : catalog) : "?";
        }
    }
}
