/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Native OS desktop notifications via java.awt.SystemTray. Not supported on every platform/window
 * manager (some Linux desktops lack a tray entirely) - fails quietly rather than throwing, since
 * this is a best-effort convenience, not a core feature.
 */
public class SystemNotifier {
    private static TrayIcon trayIcon;
    private static boolean initFailed;

    private SystemNotifier() {
    }

    public static void notify(String title, String message) {
        if (initFailed) return;
        if (trayIcon == null && !init()) return;

        trayIcon.displayMessage(title, message, TrayIcon.MessageType.NONE);
    }

    private static boolean init() {
        if (!SystemTray.isSupported()) {
            initFailed = true;
            return false;
        }

        try {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(85, 255, 85));
            g.fillRect(0, 0, 16, 16);
            g.dispose();

            trayIcon = new TrayIcon(image, "VL+");
            trayIcon.setImageAutoSize(true);
            SystemTray.getSystemTray().add(trayIcon);

            return true;
        } catch (Exception e) {
            initFailed = true;
            return false;
        }
    }
}
