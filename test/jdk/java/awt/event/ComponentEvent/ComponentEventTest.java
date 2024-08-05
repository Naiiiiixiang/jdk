/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;

/*
 * @test
 * @key headful
 * @bug 8333403
 * @summary Test performs various operations to check components events are triggered properly.
 * @run main ComponentEventTest
 */
public class ComponentEventTest {

    private static Frame frame;
    private static final int DELAY = 500;
    private static Robot robot;
    private static Component[] components;
    private static volatile boolean componentHidden;
    private static volatile boolean componentShown;
    private static volatile boolean componentMoved;
    private static volatile boolean componentResized;
    private static volatile Point compAt;
    private static volatile Dimension compSize;
    private static final java.util.Collection<ComponentEvent> events =
        Collections.synchronizedList(new ArrayList<ComponentEvent>());

    private static final ComponentListener componentListener =
        new ComponentListener() {

            @Override
            public void componentShown(ComponentEvent e) {
                System.out.println("ComponentShown: " + e.getSource());
                componentShown = true;
                events.add(e);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                System.out.println("ComponentResized: " + e.getSource());
                componentResized = true;
                events.add(e);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                System.out.println("ComponentMoved: " + e.getSource());
                componentMoved = true;
                events.add(e);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                System.out.println("ComponentHidden: " + e.getSource());
                componentHidden = true;
                events.add(e);
            }
        };

    private static void initializeGUI() {
        frame = new Frame("Component Event Test");
        frame.setLayout(new FlowLayout());

        Panel panel = new Panel();
        Button button = new Button("Button");
        Label label = new Label("Label");
        List list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        Choice choice = new Choice();
        choice.add("Red");
        choice.add("Orange");
        choice.add("Yellow");
        Checkbox checkbox = new Checkbox("Checkbox");
        Scrollbar scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, 255);
        TextField textfield = new TextField(15);
        TextArea textarea = new TextArea(5, 15);

        components = new Component[] { panel, button, label, list, choice,
            checkbox, scrollbar, textfield, textarea, frame };

        for (int i = 0; i < components.length - 1; i++) {
            components[i].addComponentListener(componentListener);
            frame.add(components[i]);
        }
        frame.addComponentListener(componentListener);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(ComponentEventTest::initializeGUI);
            robot.waitForIdle();
            robot.delay(DELAY);

            doTest();

            System.out.println("Test PASSED");
        } finally {
            EventQueue.invokeAndWait(ComponentEventTest::disposeFrame);
        }
    }

    private static void doTest()
        throws InvocationTargetException, InterruptedException {

        EventQueue.invokeAndWait(() -> {
            compAt = components[9].getLocationOnScreen();
            compSize = components[9].getSize();
        });

        robot.mouseMove(compAt.x + compSize.width / 2,
            compAt.y + compSize.height / 2);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        for (int i = 0; i < components.length; i++) {
            for (boolean state : new boolean[] { true, false }) {
                doTest(i, state);
            }
        }

        robot.delay(DELAY);

        resetValues();
        EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));

        robot.delay(DELAY);
        if (componentShown || componentHidden || componentMoved
            || componentResized) {
            printErrEvents("ComponentEvent triggered when frame is iconified");
        }

        resetValues();
        EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.NORMAL));

        robot.delay(DELAY);
        if (componentShown || componentHidden) {
            printErrEvents("ComponentEvent triggered when frame set to normal");
        }
    }

    private static void printErrEvents(String errorMsg) {
        System.err.print("Events triggered are: ");
        synchronized (events) {
            for (ComponentEvent event : events) {
                System.err.print(event + "; ");
            }
        }
        System.err.println();
        throw new RuntimeException("FAIL: " + errorMsg);
    }

    private static void doTest(int i, boolean enable)
        throws InvocationTargetException, InterruptedException {

        Component currentComponent = components[i];
        System.out
            .println("Component " + currentComponent + "is enabled " + enable);

        EventQueue.invokeAndWait(() -> {
            currentComponent.setEnabled(enable);
            revalidateFrame();
        });

        robot.delay(DELAY);

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(false);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentHidden) {
            throw new RuntimeException("FAIL: ComponentHidden not triggered for"
                + components[i].getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(false);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (componentHidden) {
            throw new RuntimeException("FAIL: ComponentHidden triggered when "
                + "setVisible(false) called for a hidden "
                + components[i].getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(true);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentShown) {
            throw new RuntimeException("FAIL: ComponentShown not triggered for "
                + components[i].getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(true);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (componentShown) {
            throw new RuntimeException("FAIL: ComponentShown triggered when "
                + "setVisible(true) called for a shown "
                + components[i].getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setLocation(currentComponent.getLocation().x + 1,
                currentComponent.getLocation().y);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentMoved) {
            throw new RuntimeException("FAIL: ComponentMoved not triggered for "
                + components[i].getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setSize(currentComponent.getSize().width + 1,
                currentComponent.getSize().height);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentResized) {
            throw new RuntimeException("FAIL: ComponentResized not triggered "
                + "when size increases for " + components[i].getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setSize(currentComponent.getSize().width - 1,
                currentComponent.getSize().height);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentResized) {
            throw new RuntimeException("FAIL: ComponentResized not triggered "
                + "when size decreases for " + components[i].getClass());
        }
    }

    private static void revalidateFrame() {
        frame.invalidate();
        frame.validate();
    }

    private static void resetValues() {
        componentShown = false;
        componentHidden = false;
        componentMoved = false;
        componentResized = false;
        events.clear();
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
