module com.drawboard {
    // JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.web;

    // Avaje modules
    requires io.avaje.inject;
    requires io.avaje.jsonb;
    requires io.avaje.jsonb.plugin;

    // Jackson (for Obsidian import)
    requires com.fasterxml.jackson.databind;

    // Logging
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;

    // Export packages
    exports com.drawboard.app;
    exports com.drawboard.domain;
    exports com.drawboard.domain.elements;
    exports com.drawboard.domain.preferences;
    exports com.drawboard.storage;
    exports com.drawboard.service;
    exports com.drawboard.ui;
    exports com.drawboard.canvas;
    exports com.drawboard.canvas.tools;
    exports com.drawboard.util;

    // Open packages for JSON serialization
    opens com.drawboard.domain to io.avaje.jsonb;
    opens com.drawboard.domain.elements to io.avaje.jsonb;
    opens com.drawboard.domain.preferences to io.avaje.jsonb;

    // Open packages for JavaFX FXML
    opens com.drawboard.ui to javafx.fxml;

    // Provide generated JSON components
    provides io.avaje.jsonb.spi.JsonbExtension with com.drawboard.domain.jsonb.GeneratedJsonComponent;

    // Provide generated DI components
    provides io.avaje.inject.spi.InjectExtension with com.drawboard.DrawboardModule;
}
