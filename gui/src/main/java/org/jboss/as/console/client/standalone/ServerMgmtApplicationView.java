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

package org.jboss.as.console.client.standalone;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.message.Message;
import org.jboss.as.console.client.shared.model.SubsystemRecord;
import org.jboss.as.console.client.widgets.TwoColumnLayout;

import java.util.List;

/**
 * Server management default view implementation.
 * Works on a LHS navigation and a all purpose content panel on the right.
 *
 * @see LHSStandaloneNavigation
 *
 * @author Heiko Braun
 * @date 2/4/11
 */
public class ServerMgmtApplicationView extends ViewImpl
        implements ServerMgmtApplicationPresenter.ServerManagementView {

    private ServerMgmtApplicationPresenter presenter;

    private TwoColumnLayout layout;
    private LayoutPanel contentCanvas;
    private LHSStandaloneNavigation lhsNavigation;

    public ServerMgmtApplicationView() {
        super();

        contentCanvas = new LayoutPanel();
        lhsNavigation = new LHSStandaloneNavigation();

        layout = new TwoColumnLayout(lhsNavigation.asWidget(), contentCanvas);

    }

    @Override
    public Widget asWidget() {
        return layout.asWidget();
    }

    @Override
    public void updateFrom(List<SubsystemRecord> subsystemRecords) {
        lhsNavigation.updateFrom(subsystemRecords);
    }

    @Override
    public void setInSlot(Object slot, IsWidget content) {

        if (slot == ServerMgmtApplicationPresenter.TYPE_MainContent) {
            if(content!=null)
                setContent(content);

        } else {
            Console.getMessageCenter().notify(
                    new Message("Unknown slot requested:" + slot)
            );
        }
    }

    private void setContent(IsWidget newContent) {
        contentCanvas.clear();
        contentCanvas.add(newContent);
    }


}
