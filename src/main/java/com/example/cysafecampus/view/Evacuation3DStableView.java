package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stable JavaFX version of the 3D evacuation interface.
 * It avoids the JavaFX 3D engine so the "Interface 3D" button stays reliable.
 */
public class Evacuation3DStableView {

    private static final int FLOOR_COUNT = 4;

    private final Stage stage;
    private final GraphController controller;
    private final Random random = new Random();

    private final Pane map = new Pane();
    private final Group routeLayer = new Group();
    private final Group zoneLayer = new Group();
    private final Group agentLayer = new Group();
    private final Group labelLayer = new Group();
    private final Group alertLayer = new Group();

    private final List<Zone2D> zones = new ArrayList<>();
    private final List<Agent2D> agents = new ArrayList<>();

    private Label titleLabel;
    private Label detailLabel;
    private Label activeLabel;
    private Label evacuatedLabel;
    private Label fireLabel;
    private Slider agentSlider;
    private Slider speedSlider;

    private int activeFloor = 0;
    private boolean running = false;
    private boolean pathsVisible = true;
    private String activeTool = "select";
    private Zone2D fireZone;
    private long lastFrame = 0;

    public Evacuation3DStableView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setLeft(buildPanel());
        root.setCenter(buildMap());

        Scene scene = new Scene(root, 1180, 720);
        stage.setTitle("CY SafeCampus - Interface 3D");
        stage.setScene(scene);
        stage.show();

        seedFloor();
        startAnimation();
    }

    private VBox buildPanel() {
        Button backButton = button("← Retour", "#546e7a");
        backButton.setOnAction(e -> new LoginView(stage, controller).show());

        Label heading = new Label("Interface 3D");
        heading.setFont(Font.font("Sans", FontWeight.BOLD, 25));
        heading.setTextFill(Color.web("#17212b"));

        Label subtitle = new Label("Étages, agents et chemins d'évacuation");
        subtitle.setTextFill(Color.web("#596b7a"));

        Button startButton = button("▶ Démarrer", "#1b7f3a");
        Button pauseButton = button("Ⅱ Pause", "#546e7a");
        Button resetButton = button("↻ Reset", "#37474f");
        Button fireButton = button("🔥 Déclencher alarme", "#c62828");
        Button pathButton = button("Chemins visibles", "#1565c0");

        startButton.setOnAction(e -> {
            running = true;
            titleLabel.setText("Évacuation lancée");
            detailLabel.setText("Les agents suivent les chemins vers la sortie ou les escaliers.");
        });
        pauseButton.setOnAction(e -> {
            running = false;
            titleLabel.setText("Simulation en pause");
            detailLabel.setText("Tu peux changer d'étage ou regarder les chemins.");
        });
        resetButton.setOnAction(e -> {
            running = false;
            fireZone = null;
            seedFloor();
        });
        fireButton.setOnAction(e -> triggerFire());
        pathButton.setOnAction(e -> {
            pathsVisible = !pathsVisible;
            routeLayer.setVisible(pathsVisible);
            pathButton.setText(pathsVisible ? "Chemins visibles" : "Chemins cachés");
        });

        GridPane simButtons = new GridPane();
        simButtons.setHgap(8);
        simButtons.setVgap(8);
        simButtons.add(startButton, 0, 0);
        simButtons.add(pauseButton, 1, 0);
        simButtons.add(resetButton, 0, 1);
        simButtons.add(fireButton, 1, 1);

        HBox floorButtons = new HBox(8);
        ToggleGroup floorGroup = new ToggleGroup();
        for (int floor = 0; floor < FLOOR_COUNT; floor++) {
            ToggleButton button = new ToggleButton(floorName(floor));
            button.setToggleGroup(floorGroup);
            button.setUserData(floor);
            button.setStyle("-fx-font-size:12px;-fx-padding:8 10;-fx-cursor:hand;");
            if (floor == 0) {
                button.setSelected(true);
            }
            button.setOnAction(e -> setActiveFloor((int) button.getUserData()));
            floorButtons.getChildren().add(button);
        }

        ToggleGroup toolGroup = new ToggleGroup();
        GridPane tools = new GridPane();
        tools.setHgap(8);
        tools.setVgap(8);
        String[][] toolData = {
            {"Sélection", "select"},
            {"Salle", "room"},
            {"Couloir", "corridor"},
            {"Sortie", "exit"},
            {"Escalier", "stair"},
            {"Agent", "agent"}
        };
        for (int i = 0; i < toolData.length; i++) {
            ToggleButton tool = new ToggleButton(toolData[i][0]);
            tool.setUserData(toolData[i][1]);
            tool.setToggleGroup(toolGroup);
            tool.setMaxWidth(Double.MAX_VALUE);
            tool.setStyle("-fx-font-size:12px;-fx-padding:8;-fx-cursor:hand;");
            if (i == 0) {
                tool.setSelected(true);
            }
            GridPane.setHgrow(tool, Priority.ALWAYS);
            tool.setOnAction(e -> {
                activeTool = (String) tool.getUserData();
                titleLabel.setText(tool.getText());
                detailLabel.setText(activeTool.equals("select")
                    ? "Clique sur un élément pour voir son nom."
                    : "Clique sur le plan pour ajouter cet élément.");
            });
            tools.add(tool, i % 2, i / 2);
        }

        agentSlider = new Slider(4, 40, 18);
        agentSlider.setShowTickLabels(true);
        agentSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            spawnAgents(newValue.intValue());
            updateStats();
        });

        speedSlider = new Slider(0.4, 2.8, 1.2);
        speedSlider.setShowTickLabels(true);

        activeLabel = statValue("0");
        evacuatedLabel = statValue("0");
        fireLabel = statValue("Aucun");

        GridPane stats = new GridPane();
        stats.setHgap(8);
        stats.setVgap(8);
        stats.add(statCard("En cours", activeLabel), 0, 0);
        stats.add(statCard("Sortis", evacuatedLabel), 1, 0);
        stats.add(statCard("Alerte", fireLabel), 0, 1, 2, 1);

        VBox panel = new VBox(18,
            backButton,
            new VBox(3, heading, subtitle),
            section("Simulation", simButtons, pathButton),
            section("Étages", floorButtons),
            section("Ajouter au plan", tools),
            section("Agents", sliderRow("Nombre", agentSlider), sliderRow("Vitesse", speedSlider)),
            section("Statut", stats)
        );
        panel.setPrefWidth(340);
        panel.setPadding(new Insets(22));
        panel.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        panel.setStyle("-fx-border-color:#d9e0e7;-fx-border-width:0 1 0 0;");
        return panel;
    }

    private StackPane buildMap() {
        map.setPrefSize(840, 720);
        map.getChildren().addAll(zoneLayer, routeLayer, agentLayer, labelLayer, alertLayer);
        map.setBackground(new Background(new BackgroundFill(Color.web("#dbeaf0"), CornerRadii.EMPTY, Insets.EMPTY)));
        map.setOnMouseClicked(e -> handleMapClick(e.getX(), e.getY()));

        titleLabel = new Label("Vue " + floorName(activeFloor));
        titleLabel.setFont(Font.font("Sans", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#17212b"));

        detailLabel = new Label("Les chemins jaunes montrent les escaliers et les sorties.");
        detailLabel.setTextFill(Color.web("#40505f"));

        HBox legend = new HBox(14,
            legend("●", "Agent", "#1565c0"),
            legend("●", "Agent paniqué", "#f44336"),
            legend("━", "Chemin", "#e1a600"),
            legend("◆", "Escalier", "#3949ab")
        );
        VBox copy = new VBox(2, titleLabel, detailLabel);
        HBox bar = new HBox(20, copy, new Region(), legend);
        HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setMaxHeight(Region.USE_PREF_SIZE);
        bar.setStyle("-fx-background-color:white;-fx-border-color:#d9e0e7;-fx-background-radius:8;-fx-border-radius:8;");

        StackPane root = new StackPane(map, bar);
        root.setBackground(new Background(new BackgroundFill(Color.web("#dbeaf0"), CornerRadii.EMPTY, Insets.EMPTY)));
        StackPane.setAlignment(bar, Pos.TOP_CENTER);
        StackPane.setMargin(bar, new Insets(18));
        return root;
    }

    private void seedFloor() {
        routeLayer.getChildren().clear();
        zoneLayer.getChildren().clear();
        agentLayer.getChildren().clear();
        labelLayer.getChildren().clear();
        alertLayer.getChildren().clear();
        zones.clear();
        agents.clear();

        Polygon floor = new Polygon(
            70, 120,
            735, 82,
            825, 560,
            150, 650
        );
        floor.setFill(Color.web("#edf7fb"));
        floor.setStroke(Color.web("#78909c"));
        floor.setStrokeWidth(3);
        zoneLayer.getChildren().add(floor);

        addPath(175, 520, 410, 520);
        addPath(410, 520, 680, 520);
        addPath(410, 520, 410, 330);
        addPath(410, 330, 650, 315);
        addPath(410, 330, 245, 245);
        addPath(650, 315, 720, 315);
        addPath(410, 330, 555, 165);

        if (activeFloor == 0) {
            addZone("Hall central", "corridor", 350, 290, 145, 90, "#b0bec5", 100);
            addZone("Couloir Nord", "corridor", 200, 215, 150, 62, "#b0bec5", 40);
            addZone("Couloir Sud", "corridor", 390, 485, 180, 62, "#b0bec5", 40);
            addZone("Bureau 1", "room", 110, 170, 118, 76, "#66bb6a", 15);
            addZone("Bureau 2", "room", 110, 300, 118, 76, "#66bb6a", 15);
            addZone("Bureau 3", "room", 110, 430, 118, 76, "#66bb6a", 15);
            addZone("Amphithéâtre", "room", 260, 515, 152, 88, "#ffca28", 60);
            addZone("Sortie Ouest", "exit", 65, 515, 118, 64, "#1976d2", 50);
            addZone("Sortie Est 1", "exit", 660, 275, 130, 64, "#1976d2", 50);
            addZone("Sortie Est 2", "exit", 660, 390, 130, 64, "#1976d2", 50);
            addZone("Sortie Est 3", "exit", 660, 515, 130, 64, "#1976d2", 50);
            addZone("Escalier 1", "stair", 520, 140, 88, 60, "#5c6bc0", 20);
            addZone("Escalier 2", "stair", 520, 315, 88, 60, "#5c6bc0", 20);
        } else {
            addZone("Couloir étage " + activeFloor, "corridor", 330, 295, 170, 86, "#b0bec5", 45);
            addZone("Salle A" + activeFloor, "room", 120, 170, 130, 82, "#66bb6a", 25);
            addZone("Salle B" + activeFloor, "room", 120, 315, 130, 82, "#66bb6a", 25);
            addZone("Salle C" + activeFloor, "room", 135, 470, 145, 82, "#66bb6a", 25);
            addZone("Laboratoire " + activeFloor, "room", 615, 185, 145, 90, "#26a69a", 30);
            addZone("Salle projet " + activeFloor, "room", 615, 430, 145, 90, "#66bb6a", 25);
            addZone("Escalier principal", "stair", 520, 145, 95, 62, "#5c6bc0", 20);
            addZone("Escalier secours", "stair", 520, 500, 95, 62, "#5c6bc0", 20);
            addZone("Sortie par escalier", "exit", 675, 315, 145, 70, "#1976d2", 50);
        }

        if (fireZone != null && fireZone.floor == activeFloor) {
            addFireMarker(fireZone.centerX, fireZone.centerY);
        }

        spawnAgents((int) agentSlider.getValue());
        titleLabel.setText("Vue " + floorName(activeFloor));
        detailLabel.setText(activeFloor == 0
            ? "RDC avec les sorties principales."
            : "Étage " + activeFloor + " avec les escaliers vers l'évacuation.");
        updateStats();
    }

    private void addZone(String name, String type, double x, double y, double w, double h, String fill, int capacity) {
        Shape shape;
        if (type.equals("stair")) {
            Polygon diamond = new Polygon(
                x + w / 2, y,
                x + w, y + h / 2,
                x + w / 2, y + h,
                x, y + h / 2
            );
            shape = diamond;
        } else {
            Rectangle rectangle = new Rectangle(x, y, w, h);
            rectangle.setArcWidth(type.equals("corridor") ? 18 : 10);
            rectangle.setArcHeight(type.equals("corridor") ? 18 : 10);
            shape = rectangle;
        }
        shape.setFill(Color.web(fill));
        shape.setOpacity(1.0);
        shape.setStroke(Color.web(type.equals("stair") ? "#1a237e" : "#263238"));
        shape.setStrokeWidth(type.equals("stair") ? 4 : 3);
        shape.setOnMouseClicked(e -> {
            titleLabel.setText(name);
            detailLabel.setText(typeLabel(type) + " · capacité " + capacity + " · " + floorName(activeFloor));
            e.consume();
        });
        zoneLayer.getChildren().add(shape);

        Zone2D zone = new Zone2D(name, type, activeFloor, x + w / 2, y + h / 2, capacity, shape);
        zones.add(zone);

        Label nameLabel = readableLabel(name, 13, FontWeight.BOLD);
        nameLabel.setLayoutX(x + 8);
        nameLabel.setLayoutY(y + h / 2 - 12);
        nameLabel.setMaxWidth(w - 16);

        Label capacityLabel = readableLabel("0/" + capacity, 10, FontWeight.NORMAL);
        capacityLabel.setLayoutX(x + w / 2 - 16);
        capacityLabel.setLayoutY(y + h + 4);

        labelLayer.getChildren().addAll(nameLabel, capacityLabel);
    }

    private void addPath(double x1, double y1, double x2, double y2) {
        Line shadow = new Line(x1, y1, x2, y2);
        shadow.setStroke(Color.web("#5d4300"));
        shadow.setStrokeWidth(10);
        shadow.setOpacity(0.26);

        Line line = new Line(x1, y1, x2, y2);
        line.setStroke(Color.web("#f9a825"));
        line.setStrokeWidth(7);
        line.getStrokeDashArray().setAll(16.0, 8.0);

        routeLayer.getChildren().addAll(shadow, line);
    }

    private void spawnAgents(int count) {
        agentLayer.getChildren().clear();
        agents.clear();

        List<Zone2D> starts = zones.stream()
            .filter(zone -> zone.type.equals("room"))
            .toList();
        if (starts.isEmpty()) {
            return;
        }

        for (int i = 0; i < count; i++) {
            Zone2D start = starts.get(i % starts.size());
            Point2D startPoint = new Point2D(
                start.centerX + (random.nextDouble() - 0.5) * 36,
                start.centerY + (random.nextDouble() - 0.5) * 30
            );
            List<Point2D> route = routeFor(startPoint);
            Agent2D agent = new Agent2D(startPoint, route, i % 4 == 0);
            agents.add(agent);
            agentLayer.getChildren().add(agent.node);
        }
    }

    private List<Point2D> routeFor(Point2D start) {
        Point2D corridor = new Point2D(410, 330);
        Point2D exit = activeFloor == 0 ? new Point2D(725, 315) : new Point2D(565, 160);
        Point2D finalPoint = activeFloor == 0 ? new Point2D(760, 315) : new Point2D(695, 315);
        return new ArrayList<>(List.of(start, corridor, exit, finalPoint));
    }

    private void startAnimation() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrame == 0) {
                    lastFrame = now;
                    return;
                }
                double delta = Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
                lastFrame = now;
                if (running) {
                    moveAgents(delta);
                }
                pulseAlert(now);
            }
        };
        timer.start();
    }

    private void moveAgents(double delta) {
        double speed = 65 * speedSlider.getValue();
        for (Agent2D agent : agents) {
            if (agent.evacuated) {
                continue;
            }
            if (agent.target >= agent.route.size()) {
                agent.evacuated = true;
                agent.node.setOpacity(0.18);
                continue;
            }

            Point2D target = agent.route.get(agent.target);
            Point2D current = new Point2D(agent.node.getLayoutX(), agent.node.getLayoutY());
            double distance = current.distance(target);
            double step = speed * delta;

            if (distance <= step) {
                agent.node.setLayoutX(target.getX());
                agent.node.setLayoutY(target.getY());
                agent.target++;
            } else {
                double ratio = step / distance;
                agent.node.setLayoutX(current.getX() + (target.getX() - current.getX()) * ratio);
                agent.node.setLayoutY(current.getY() + (target.getY() - current.getY()) * ratio);
            }
        }
        updateStats();
    }

    private void triggerFire() {
        List<Zone2D> candidates = zones.stream()
            .filter(zone -> !zone.type.equals("exit") && !zone.type.equals("stair"))
            .toList();
        if (candidates.isEmpty()) {
            return;
        }
        fireZone = candidates.get(random.nextInt(candidates.size()));
        fireZone.shape.setFill(Color.web("#ffcdd2"));
        addFireMarker(fireZone.centerX, fireZone.centerY);
        running = true;
        titleLabel.setText("ALERTE incendie");
        detailLabel.setText("Départ de feu : " + fireZone.name + ". Les agents se dirigent vers la sortie.");
        updateStats();
    }

    private void addFireMarker(double x, double y) {
        alertLayer.getChildren().clear();
        Circle fire = new Circle(x, y, 18, Color.web("#ff3d00"));
        fire.setOpacity(0.85);
        Label icon = new Label("🔥");
        icon.setFont(Font.font(22));
        icon.setLayoutX(x - 12);
        icon.setLayoutY(y - 18);
        alertLayer.getChildren().addAll(fire, icon);
    }

    private void pulseAlert(long now) {
        if (alertLayer.getChildren().isEmpty()) {
            return;
        }
        double scale = 1.0 + Math.sin(now / 130_000_000.0) * 0.08;
        alertLayer.setScaleX(scale);
        alertLayer.setScaleY(scale);
    }

    private void handleMapClick(double x, double y) {
        if (activeTool.equals("select")) {
            titleLabel.setText("Vue " + floorName(activeFloor));
            detailLabel.setText("Clique sur un élément, démarre la simulation ou change d'étage.");
            return;
        }

        if (activeTool.equals("agent")) {
            agentSlider.setValue(Math.min(agentSlider.getMax(), agentSlider.getValue() + 1));
            titleLabel.setText("Agent ajouté");
            detailLabel.setText("Un nouvel agent suit maintenant le chemin d'évacuation.");
            return;
        }

        String name = switch (activeTool) {
            case "corridor" -> "Nouveau couloir";
            case "exit" -> "Nouvelle sortie";
            case "stair" -> "Nouvel escalier";
            default -> "Nouvelle salle";
        };
        String fill = switch (activeTool) {
            case "corridor" -> "#dce3e8";
            case "exit" -> "#1976d2";
            case "stair" -> "#5c6bc0";
            default -> "#66bb6a";
        };
        addZone(name, activeTool, x - 48, y - 28, activeTool.equals("corridor") ? 130 : 96, 56, fill, 20);
        titleLabel.setText(name + " ajouté");
        detailLabel.setText("Ajouté sur " + floorName(activeFloor) + ".");
    }

    private void setActiveFloor(int floor) {
        activeFloor = floor;
        fireZone = null;
        seedFloor();
    }

    private void updateStats() {
        long evacuated = agents.stream().filter(agent -> agent.evacuated).count();
        long active = agents.size() - evacuated;
        activeLabel.setText(String.valueOf(active));
        evacuatedLabel.setText(String.valueOf(evacuated));
        fireLabel.setText(fireZone == null ? "Aucun" : fireZone.name);
    }

    private VBox section(String title, Node... nodes) {
        Label label = new Label(title);
        label.setFont(Font.font("Sans", FontWeight.BOLD, 13));
        label.setTextFill(Color.web("#263b4a"));
        VBox box = new VBox(10);
        box.getChildren().add(label);
        box.getChildren().addAll(nodes);
        box.setPadding(new Insets(14, 0, 0, 0));
        box.setStyle("-fx-border-color:#d9e0e7;-fx-border-width:1 0 0 0;");
        return box;
    }

    private HBox sliderRow(String text, Slider slider) {
        Label label = new Label(text);
        label.setPrefWidth(64);
        Label value = new Label(slider == agentSlider ? String.valueOf((int) slider.getValue()) : String.format("%.1f", slider.getValue()));
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
            value.setText(slider == agentSlider ? String.valueOf(newValue.intValue()) : String.format("%.1f", newValue.doubleValue())));
        HBox row = new HBox(10, label, slider, value);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private VBox statCard(String title, Label value) {
        Label label = new Label(title);
        label.setTextFill(Color.web("#596b7a"));
        VBox card = new VBox(4, label, value);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color:#f8fafb;-fx-border-color:#d9e0e7;-fx-background-radius:8;-fx-border-radius:8;");
        return card;
    }

    private Label statValue(String value) {
        Label label = new Label(value);
        label.setFont(Font.font("Sans", FontWeight.BOLD, 18));
        label.setTextFill(Color.web("#111111"));
        return label;
    }

    private HBox legend(String mark, String text, String color) {
        Label markLabel = new Label(mark);
        markLabel.setFont(Font.font("Sans", FontWeight.BOLD, 14));
        markLabel.setTextFill(Color.web(color));
        Label textLabel = new Label(text);
        textLabel.setTextFill(Color.web("#263b4a"));
        return new HBox(5, markLabel, textLabel);
    }

    private Label readableLabel(String text, int size, FontWeight weight) {
        Label label = new Label(text);
        label.setFont(Font.font("Sans", weight, size));
        label.setTextFill(Color.BLACK);
        label.setOpacity(1.0);
        label.setStyle("-fx-background-color:white;-fx-background-radius:5;-fx-padding:2 5;-fx-border-color:#cfd8dc;-fx-border-radius:5;");
        return label;
    }

    private Button button(String text, String color) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;-fx-font-size:12px;-fx-padding:9;-fx-background-radius:8;-fx-cursor:hand;");
        return button;
    }

    private String floorName(int floor) {
        return floor == 0 ? "RDC" : "Étage " + floor;
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "corridor" -> "Couloir";
            case "exit" -> "Sortie";
            case "stair" -> "Escalier";
            default -> "Salle";
        };
    }

    private static class Zone2D {
        final String name;
        final String type;
        final int floor;
        final double centerX;
        final double centerY;
        final int capacity;
        final Shape shape;

        Zone2D(String name, String type, int floor, double centerX, double centerY, int capacity, Shape shape) {
            this.name = name;
            this.type = type;
            this.floor = floor;
            this.centerX = centerX;
            this.centerY = centerY;
            this.capacity = capacity;
            this.shape = shape;
        }
    }

    private static class Agent2D {
        final Group node;
        final List<Point2D> route;
        int target = 1;
        boolean evacuated = false;

        Agent2D(Point2D start, List<Point2D> route, boolean panicked) {
            this.route = route;
            Circle halo = new Circle(0, 0, 18, Color.web(panicked ? "#ffcdd2" : "#bbdefb"));
            halo.setOpacity(1.0);
            halo.setStroke(Color.WHITE);
            halo.setStrokeWidth(3);
            Circle body = new Circle(0, 0, 9, Color.web(panicked ? "#d50000" : "#0d47a1"));
            Line trunk = new Line(0, 7, 0, 22);
            trunk.setStroke(Color.web(panicked ? "#d50000" : "#0d47a1"));
            trunk.setStrokeWidth(4);
            Line arms = new Line(-9, 13, 9, 13);
            arms.setStroke(Color.web(panicked ? "#d50000" : "#0d47a1"));
            arms.setStrokeWidth(4);
            Line legA = new Line(0, 22, -8, 34);
            Line legB = new Line(0, 22, 8, 34);
            legA.setStroke(trunk.getStroke());
            legB.setStroke(trunk.getStroke());
            legA.setStrokeWidth(4);
            legB.setStrokeWidth(4);
            this.node = new Group(halo, body, trunk, arms, legA, legB);
            this.node.setLayoutX(start.getX());
            this.node.setLayoutY(start.getY());
        }
    }
}
