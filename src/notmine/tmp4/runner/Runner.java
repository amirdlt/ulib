package notmine.tmp4.runner;

import notmine.tmp4.controller.Controller;

import javax.swing.*;

public class Runner {
    public static void run() {
        SwingUtilities.invokeLater(new Controller());
    }
}
