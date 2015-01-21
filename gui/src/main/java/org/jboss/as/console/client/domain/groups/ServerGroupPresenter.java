/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.jboss.as.console.client.domain.groups;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.MainLayoutPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.core.SuspendableView;
import org.jboss.as.console.client.domain.events.StaleModelEvent;
import org.jboss.as.console.client.domain.hosts.HostMgmtPresenter;
import org.jboss.as.console.client.domain.model.*;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.jvm.*;
import org.jboss.as.console.client.shared.properties.*;
import org.jboss.as.console.client.shared.util.DMRUtil;
import org.jboss.as.console.client.v3.stores.domain.ServerStore;
import org.jboss.as.console.client.widgets.forms.ApplicationMetaData;
import org.jboss.as.console.spi.AccessControl;
import org.jboss.as.console.spi.OperationMode;
import org.jboss.as.console.spi.SearchIndex;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;
import org.jboss.gwt.flow.client.Async;
import org.jboss.gwt.flow.client.Control;
import org.jboss.gwt.flow.client.Function;
import org.jboss.gwt.flow.client.Outcome;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.jboss.as.console.spi.OperationMode.Mode.DOMAIN;
import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * Maintains a single server group.
 *
 * @author Heiko Braun
 * @date 2/16/11
 */
public class ServerGroupPresenter
        extends Presenter<ServerGroupPresenter.MyView, ServerGroupPresenter.MyProxy>
        implements JvmManagement, PropertyManagement {

    @ProxyCodeSplit
    @NameToken(NameTokens.ServerGroupPresenter)
    @OperationMode(DOMAIN)
    @AccessControl(resources = {
            "/server-group=*",
            "opt://server-group={selected.entity}/system-property=*"},
            recursive = false)
    @SearchIndex(keywords = {"group", "server-group", "profile", "socket-binding", "jvm"})
    public interface MyProxy extends Proxy<ServerGroupPresenter>, Place {}


    public interface MyView extends SuspendableView {
        void setPresenter(ServerGroupPresenter presenter);

        void updateSocketBindings(List<String> result);
        void setJvm(ServerGroupRecord group, Jvm jvm);
        void setProperties(ServerGroupRecord group, List<PropertyRecord> properties);
        void setPreselection(String preselection);
        void updateProfiles(List<ProfileRecord> result);

        void updateFrom(ServerGroupRecord group);
    }


    private ServerGroupStore serverGroupStore;
    private ProfileStore profileStore;

    private DefaultWindow window;
    private DefaultWindow propertyWindow;

    private DispatchAsync dispatcher;
    private BeanFactory factory;
    private ApplicationMetaData propertyMetaData;
    private final ServerStore serverStore;

    private List<ProfileRecord> existingProfiles;
    private List<String> existingSockets;
    private String preselection;


    @Inject
    public ServerGroupPresenter(
            EventBus eventBus, MyView view, MyProxy proxy,
            ServerGroupStore serverGroupStore,
            ProfileStore profileStore,
            DispatchAsync dispatcher, BeanFactory factory,
            ApplicationMetaData propertyMetaData, ServerStore serverStore) {
        super(eventBus, view, proxy);

        this.serverGroupStore = serverGroupStore;
        this.profileStore = profileStore;
        this.dispatcher = dispatcher;
        this.factory = factory;
        this.propertyMetaData = propertyMetaData;
        this.serverStore = serverStore;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    public void prepareFromRequest(PlaceRequest request) {
        super.prepareFromRequest(request);

        final String action = request.getParameter("action", null);
        if ("new".equals(action)) {
            if (existingProfiles == null || existingSockets == null) {
                List<Function<Void>> functions = new LinkedList<Function<Void>>();
                if (existingProfiles == null) {
                    functions.add(new Function<Void>() {
                        @Override
                        public void execute(final Control<Void> control) {
                            profileStore.loadProfiles(new SimpleCallback<List<ProfileRecord>>() {
                                @Override
                                public void onSuccess(List<ProfileRecord> result) {
                                    existingProfiles = result;
                                }
                            });
                        }
                    });
                }
                if (existingSockets == null) {
                    functions.add(new Function<Void>() {
                        @Override
                        public void execute(final Control<Void> control) {
                            serverGroupStore.loadSocketBindingGroupNames(new SimpleCallback<List<String>>() {
                                @Override
                                public void onSuccess(List<String> result) {
                                    existingSockets = result;
                                }
                            });
                        }
                    });
                }
                Outcome<Void> wizardOutcome = new Outcome<Void>() {
                    @Override
                    public void onFailure(final Void context) {
                        Log.error("Cannot launch new server group wizard");
                    }

                    @Override
                    public void onSuccess(final Void context) {
                        launchNewGroupDialoge();
                    }
                };
                //noinspection unchecked
                new Async<Void>().parallel(null, wizardOutcome, functions.toArray(new Function[functions.size()]));
            } else {
                launchNewGroupDialoge();
            }
        }

        preselection = request.getParameter("group", null);
        getView().setPreselection(preselection);
    }

    @Override
    protected void onReset() {

        super.onReset();

        profileStore.loadProfiles(new SimpleCallback<List<ProfileRecord>>() {
            @Override
            public void onSuccess(List<ProfileRecord> result) {
                existingProfiles = result;
                getView().updateProfiles(result);
            }
        });

        serverGroupStore.loadSocketBindingGroupNames(new SimpleCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> result) {
                existingSockets = result;

                getView().updateSocketBindings(result);
            }
        });

        //loadServerGroups();

    }

    private void staleModel() {
        fireEvent(new StaleModelEvent(StaleModelEvent.SERVER_GROUPS));
    }

    @Deprecated
    private void loadServerGroups() {
       /* serverGroupStore.loadServerGroups(new SimpleCallback<List<ServerGroupRecord>>() {
            @Override
            public void onSuccess(List<ServerGroupRecord> result) {

                for (ServerGroupRecord groupRecord : result) {
                    if(groupRecord.getName().equals(serverStore.getSelectedGroup()))
                    {
                        getView().updateFrom(groupRecord);
                        break;
                    }
                }

            }
        });*/
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainLayoutPresenter.TYPE_Popup, this);
    }

    // ----------------------------------------------------------------


    public void onDeleteGroup(final ServerGroupRecord group) {

        serverGroupStore.delete(group, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean wasSuccessful) {
                if (wasSuccessful) {
                    Console.info(Console.MESSAGES.deleted(group.getName()));
                } else {
                    Console.error(Console.MESSAGES.deletionFailed(group.getName()));
                }

                staleModel();

                loadServerGroups();
            }
        });
    }

    public void createNewGroup(final ServerGroupRecord newGroup) {

        closeDialoge();

        serverGroupStore.create(newGroup, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {

                if (success) {

                    Console.info(Console.MESSAGES.added(newGroup.getName()));
                    loadServerGroups();

                } else {
                    Console.error(Console.MESSAGES.addingFailed(newGroup.getName()));
                }

                staleModel();

            }
        });
    }

    public void onSaveChanges(final ServerGroupRecord group, Map<String,Object> changeset) {

        serverGroupStore.save(group.getName(), changeset, new SimpleCallback<Boolean>() {

            @Override
            public void onSuccess(Boolean wasSuccessful) {
                if(wasSuccessful)
                {
                    Console.info(Console.MESSAGES.modified(group.getName()));
                }
                else
                {
                    Console.info(Console.MESSAGES.modificationFailed(group.getName()));
                }

                loadServerGroups();
            }
        });

    }

    public void launchNewGroupDialoge() {

        window = new DefaultWindow(Console.MESSAGES.createTitle("Server Group"));
        window.setWidth(480);
        window.setHeight(360);
        window.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {

            }
        });

        /*window.trapWidget(
                new NewServerGroupWizard(this, existingProfiles, existingSockets).asWidget()
        );*/

        window.setGlassEnabled(true);
        window.center();
    }

    public void closeDialoge()
    {
        if(window!=null) window.hide();
    }

    public void onUpdateJvm(final String groupName, String jvmName, Map<String, Object> changedValues) {

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("jvm", jvmName);

        UpdateJvmCmd cmd = new UpdateJvmCmd(dispatcher, factory, propertyMetaData, address);
        cmd.execute(changedValues, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroups();
            }
        });

    }

    public void onCreateJvm(final String groupName, Jvm jvm) {

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("jvm", jvm.getName());

        CreateJvmCmd cmd = new CreateJvmCmd(dispatcher, factory, address);
        cmd.execute(jvm, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroups();
            }
        });

    }

    public void onDeleteJvm(final String groupName, Jvm jvm) {

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("jvm", jvm.getName());

        DeleteJvmCmd cmd = new DeleteJvmCmd(dispatcher, factory, address);
        cmd.execute(new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroups();
            }
        });
    }

    public void closePropertyDialoge() {
        propertyWindow.hide();
    }

    public void launchNewPropertyDialoge(String group) {

        propertyWindow = new DefaultWindow(Console.MESSAGES.createTitle("System Property"));
        propertyWindow.setWidth(480);
        propertyWindow.setHeight(360);

        propertyWindow.trapWidget(
                new NewPropertyWizard(this, group, true).asWidget()
        );

        propertyWindow.setGlassEnabled(true);
        propertyWindow.center();
    }

    public void onCreateProperty(final String groupName, final PropertyRecord prop)
    {
        if(propertyWindow!=null && propertyWindow.isShowing())
        {
            propertyWindow.hide();
        }

        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("system-property", prop.getKey());

        CreatePropertyCmd cmd = new CreatePropertyCmd(dispatcher, factory, address);
        cmd.execute(prop, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroups();
            }
        });
    }

    public void onDeleteProperty(final String groupName, final PropertyRecord prop)
    {
        ModelNode address = new ModelNode();
        address.add("server-group", groupName);
        address.add("system-property", prop.getKey());

        DeletePropertyCmd cmd = new DeletePropertyCmd(dispatcher,factory,address);
        cmd.execute(prop, new SimpleCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                loadServerGroups();
            }
        });
    }

    @Override
    public void onChangeProperty(String groupName, PropertyRecord prop) {
        // do nothing
    }

    public void loadJVMConfiguration(final ServerGroupRecord group) {
        serverGroupStore.loadJVMConfiguration(group, new SimpleCallback<Jvm>() {
            @Override
            public void onSuccess(Jvm jvm) {
                getView().setJvm(group, jvm);
            }
        });
    }

    public void loadProperties(final ServerGroupRecord group) {
        serverGroupStore.loadProperties(group, new SimpleCallback<List<PropertyRecord>>() {
            @Override
            public void onSuccess(List<PropertyRecord> properties) {
                getView().setProperties(group, properties);
            }
        });
    }

    public void launchCopyWizard(final ServerGroupRecord orig) {
        window = new DefaultWindow("New Server Group");
        window.setWidth(400);
        window.setHeight(320);

       /* window.trapWidget(
                new CopyGroupWizard(ServerGroupPresenter.this, orig).asWidget()
        );*/

        window.setGlassEnabled(true);
        window.center();
    }

    public void onSaveCopy(final ServerGroupRecord orig, final ServerGroupRecord newGroup) {
        window.hide();

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_RESOURCE_OPERATION);
        operation.get(ADDRESS).add("server-group", orig.getName());
        operation.get(RECURSIVE).set(true);

        dispatcher.execute(new DMRAction(operation, false), new AsyncCallback<DMRResponse>() {
            @Override
            public void onFailure(Throwable caught) {
                Console.error("Failed to read server-group: "+orig.getName(), caught.getMessage());
            }

            @Override
            public void onSuccess(DMRResponse result) {

                ModelNode response = result.get();

                if(response.isFailure())
                {
                    Console.error("Failed to read server-group: "+orig.getName(), response.getFailureDescription());
                }
                else
                {
                    ModelNode model = response.get("result").asObject();
                    model.remove("name");

                    // re-create node

                    ModelNode compositeOp = new ModelNode();
                    compositeOp.get(OP).set(COMPOSITE);
                    compositeOp.get(ADDRESS).setEmptyList();

                    List<ModelNode> steps = new ArrayList<ModelNode>();

                    final ModelNode rootResourceOp = new ModelNode();
                    rootResourceOp.get(OP).set(ADD);
                    rootResourceOp.get(ADDRESS).add("server-group", newGroup.getName());

                    steps.add(rootResourceOp);

                    DMRUtil.copyResourceValues(model, rootResourceOp, steps);

                    compositeOp.get(STEPS).set(steps);

                    dispatcher.execute(new DMRAction(compositeOp), new SimpleCallback<DMRResponse>() {
                        @Override
                        public void onSuccess(DMRResponse dmrResponse) {
                            ModelNode response = dmrResponse.get();

                            if(response.isFailure())
                            {
                                Console.error("Failed to copy server-group", response.getFailureDescription());
                            }
                            else
                            {
                                Console.info("Successfully copied server-group '"+newGroup.getName()+"'");
                            }

                            loadServerGroups();
                        }
                    });

                }

            }

        });
    }

}
