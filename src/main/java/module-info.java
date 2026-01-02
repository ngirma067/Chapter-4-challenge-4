module org.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    opens org.example to javafx.fxml, javafx.graphics;
    exports org.example;
}