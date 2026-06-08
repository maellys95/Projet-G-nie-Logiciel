package com.example.cysafecampus.view;

import com.example.cysafecampus.controller.GraphController;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * JavaFX 3D adaptation of the HTML/Three.js evacuation mockup.
 * It gives the project a native multi-floor evacuation visualization.
 */
public class Evacuation3DView {

    private static final int FLOOR_COUNT = 4;
    private static final double FLOOR_GAP = 120.0;

    private final Stage stage;
    private final GraphController controller;
    private final Random random = new Random();

    private final Group world = new Group();
    private final List<Group> floorGroups = new ArrayList<>();
    private final List<Zone3D> zones = new ArrayList<>();
    private final List<Agent3D> agents = new ArrayList<>();
    private final List<Node> paths = new ArrayList<>();

    private Label titleLbl;
    private Label detailLbl;
    private Label evacuatedLbl;
    private Label activeLbl;
    private Label fireLbl;
    private Slider agentCountSlider;
    private Slider speedSlider;
    private ToggleGroup floorToggleGroup;

    private int activeFloor = 0;
    private String activeTool = "select";
    private boolean running = false;
    private Sphere fireNode;
    private Zone3D fireZone;
    private long lastFrame = 0;

    public Evacuation3DView(Stage stage, GraphController controller) {
        this.stage = stage;
        this.controller = controller;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.setLeft(buildPanel());
        root.setCenter(buildScenePane());

        seedBuilding();

        Scene scene = new Scene(root, 1120, 700, true);
        stage.setTitle("CY SafeCampus - Evacuation 3D");
        stage.setScene(scene);
        stage.show();

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
                    stepAgents(delta);
                }

                animateFire(now);
            }
        };
        timer.start();
    }

    private VBox buildPanel() {
        Button backBtn = button("← Retour", "#546e7a");
        backBtn.setOnAction(e -> new LoginView(stage, controller).show());

        Label heading = new Label("Evacuation 3D");
        heading.setFont(Font.font("Sans", FontWeight.BOLD, 24));
        heading.setTextFill(Color.web("#17212b"));

        Label sub = new Label("Agents, graphes et incendie");
        sub.setTextFill(Color.web("#697887"));

        VBox brand = new VBox(4, heading, sub);

        Button startBtn = button("Démarrer", "#12786f");
        Button pauseBtn = button("Pause", "#546e7a");
        Button resetBtn = button("Réinitialiser", "#455a64");
        Button fireBtn = button("Feu aléatoire", "#c94032");
        startBtn.setOnAction(e -> {
            running = true;
            titleLbl.setText("Évacuation lancée");
            detailLbl.setText("Les agents suivent les parcours colorés vers la sortie.");
        });
        pauseBtn.setOnAction(e -> {
            running = false;
            titleLbl.setText("Simulation en pause");
            detailLbl.setText("Tu peux changer d'étage ou modifier le plan.");
        });
        resetBtn.setOnAction(e -> {
            running = false;
            seedBuilding();
        });
        fireBtn.setOnAction(e -> randomFire());

        GridPane simGrid = new GridPane();
        simGrid.setHgap(8);
        simGrid.setVgap(8);
        simGrid.add(startBtn, 0, 0);
        simGrid.add(pauseBtn, 1, 0);
        simGrid.add(resetBtn, 0, 1);
        simGrid.add(fireBtn, 1, 1);

        floorToggleGroup = new ToggleGroup();
        HBox floorButtons = new HBox(6);
        for (int i = 0; i < FLOOR_COUNT; i++) {
            ToggleButton btn = new ToggleButton(floorLabel(i));
            btn.setToggleGroup(floorToggleGroup);
            btn.setUserData(i);
            btn.setStyle("-fx-font-size:11px;-fx-padding:7 10;");
            if (i == 0) btn.setSelected(true);
            btn.setOnAction(e -> setActiveFloor((int) btn.getUserData()));
            floorButtons.getChildren().add(btn);
        }

        ToggleGroup toolGroup = new ToggleGroup();
        GridPane tools = new GridPane();
        tools.setHgap(8);
        tools.setVgap(8);
        String[][] toolRows = {
            {"Sélection", "select"}, {"Salle", "classroom"},
            {"Amphi", "amphi"}, {"Couloir", "corridor"},
            {"Sortie", "exit"}, {"Agent", "agent"}
        };
        for (int i = 0; i < toolRows.length; i++) {
            ToggleButton btn = new ToggleButton(toolRows[i][0]);
            btn.setUserData(toolRows[i][1]);
            btn.setToggleGroup(toolGroup);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setStyle("-fx-font-size:12px;-fx-padding:8;");
            GridPane.setHgrow(btn, Priority.ALWAYS);
            if (i == 0) btn.setSelected(true);
            btn.setOnAction(e -> {
                activeTool = (String) btn.getUserData();
                titleLbl.setText(btn.getText());
                detailLbl.setText(activeTool.equals("select")
                    ? "Clique sur un élément du plan."
                    : "Clique sur la vue 3D pour placer cet élément.");
            });
            tools.add(btn, i % 2, i / 2);
        }

        agentCountSlider = new Slider(8, 48, 24);
        agentCountSlider.setShowTickLabels(true);
        agentCountSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            spawnAgents(newValue.intValue());
            updateStats();
        });
        speedSlider = new Slider(0.4, 2.5, 1.2);
        speedSlider.setShowTickLabels(true);

        evacuatedLbl = statValue("0");
        activeLbl = statValue("24");
        fireLbl = statValue("Aucun");
        GridPane stats = new GridPane();
        stats.setHgap(10);
        stats.setVgap(6);
        stats.add(statBox("Évacués", evacuatedLbl), 0, 0);
        stats.add(statBox("En cours", activeLbl), 1, 0);
        stats.add(statBox("Feu", fireLbl), 2, 0);

        VBox panel = new VBox(18,
            backBtn,
            brand,
            section("Simulation", simGrid),
            section("Étage", floorButtons),
            section("Ajouter", tools),
            section("Agents", labeledSlider("Nombre", agentCountSlider), labeledSlider("Vitesse", speedSlider)),
            stats
        );
        panel.setPadding(new Insets(22));
        panel.setPrefWidth(340);
        panel.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        panel.setStyle("-fx-border-color:#d9e0e7;-fx-border-width:0 1 0 0;");
        return panel;
    }

    private StackPane buildScenePane() {
        SubScene subScene = new SubScene(world, 780, 700, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#f3f6f8"));

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);
        camera.setTranslateX(520);
        camera.setTranslateY(-470);
        camera.setTranslateZ(-760);
        camera.setRotationAxis(Rotate.X_AXIS);
        camera.setRotate(-34);
        subScene.setCamera(camera);

        AmbientLight ambient = new AmbientLight(Color.rgb(235, 242, 246));
        PointLight light = new PointLight(Color.WHITE);
        light.setTranslateX(-260);
        light.setTranslateY(-420);
        light.setTranslateZ(-280);
        world.getChildren().addAll(ambient, light);

        subScene.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> handleSceneClick());

        titleLbl = new Label("Bâtiment principal");
        titleLbl.setFont(Font.font("Sans", FontWeight.BOLD, 16));
        titleLbl.setTextFill(Color.web("#17212b"));
        detailLbl = new Label("Sélectionne un outil ou lance une évacuation.");
        detailLbl.setTextFill(Color.web("#40505f"));

        VBox topText = new VBox(2, titleLbl, detailLbl);
        HBox legend = new HBox(16,
            legend("●", "Agents", "#2e7de1"),
            legend("●", "Incendie", "#e34a2f"),
            legend("●", "Parcours", "#e1b145")
        );
        HBox topbar = new HBox(20, topText, new Region(), legend);
        HBox.setHgrow(topbar.getChildren().get(1), Priority.ALWAYS);
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.setPadding(new Insets(12, 14, 12, 14));
        topbar.setMaxWidth(Double.MAX_VALUE);
        topbar.setStyle("-fx-background-color:rgba(255,255,255,0.88);-fx-border-color:#d9e0e7;-fx-background-radius:8;-fx-border-radius:8;");

        StackPane pane = new StackPane(subScene, topbar);
        subScene.widthProperty().bind(pane.widthProperty());
        subScene.heightProperty().bind(pane.heightProperty());
        StackPane.setAlignment(topbar, Pos.TOP_CENTER);
        StackPane.setMargin(topbar, new Insets(18));
        pane.setBackground(new Background(new BackgroundFill(Color.web("#edf1f5"), CornerRadii.EMPTY, Insets.EMPTY)));
        return pane;
    }

    private void seedBuilding() {
        world.getChildren().removeIf(node -> !(node instanceof LightBase));
        floorGroups.clear();
        zones.clear();
        agents.clear();
        paths.clear();
        fireNode = null;
        fireZone = null;

        for (int floor = 0; floor < FLOOR_COUNT; floor++) {
            Group floorGroup = new Group();
            floorGroup.setTranslateY(-floor * FLOOR_GAP);
            floorGroups.add(floorGroup);
            world.getChildren().add(floorGroup);

            Box slab = box(640, 8, 440, "#e7edf1");
            slab.setTranslateY(0);
            floorGroup.getChildren().add(slab);

            addZone(floorGroup, "Couloir central", "corridor", floor, 0, 0, 500, 64, "#b7c1cb");
            addZone(floorGroup, "Salle A" + (floor + 1), "classroom", floor, -210, -125, 120, 82, "#83b7c8");
            addZone(floorGroup, "Salle B" + (floor + 1), "classroom", floor, -70, -125, 110, 82, "#83b7c8");
            addZone(floorGroup, "Amphi " + (floor + 1), "amphi", floor, 150, -125, 150, 95, "#d7a955");
            addZone(floorGroup, "Salle C" + (floor + 1), "classroom", floor, -170, 130, 120, 82, "#83b7c8");
            addZone(floorGroup, "Salle D" + (floor + 1), "classroom", floor, 40, 130, 120, 82, "#83b7c8");
            addZone(floorGroup, "Sortie " + (floor + 1), "exit", floor, 270, 0, 55, 100, "#28a56f");

            Cylinder stair = new Cylinder(18, FLOOR_GAP, 16);
            stair.setMaterial(material("#e1b145"));
            stair.setTranslateX(230);
            stair.setTranslateZ(145);
            stair.setTranslateY(-FLOOR_GAP / 2.0);
            if (floor < FLOOR_COUNT - 1) floorGroup.getChildren().add(stair);
        }

        spawnAgents(24);
        setActiveFloor(0);
        updateStats();
    }

    private void addZone(Group floorGroup, String name, String type, int floor, double x, double z, double w, double d, String color) {
        Group group = new Group();
        group.setTranslateX(x);
        group.setTranslateZ(z);

        Box base = box(w, 10, d, color);
        base.setTranslateY(-10);
        group.getChildren().add(base);

        if (!type.equals("corridor") && !type.equals("exit")) {
            group.getChildren().add(wall(0, -d / 2, w, 6));
            group.getChildren().add(wall(0, d / 2, w, 6));
            group.getChildren().add(wall(-w / 2, 0, 6, d));
            group.getChildren().add(wall(w / 2, 0, 6, d));
        }

        Zone3D zone = new Zone3D(name, type, floor, x, z, w, d, group);
        group.setUserData(zone);
        floorGroup.getChildren().add(group);
        zones.add(zone);
    }

    private Box wall(double x, double z, double w, double d) {
        Box wall = box(w, 44, d, "#f9fbfc");
        wall.setTranslateX(x);
        wall.setTranslateY(-32);
        wall.setTranslateZ(z);
        return wall;
    }

    private void spawnAgents(int count) {
        agents.forEach(agent -> agent.floorGroup.getChildren().remove(agent.node));
        paths.forEach(path -> floorGroups.forEach(group -> group.getChildren().remove(path)));
        agents.clear();
        paths.clear();

        List<Zone3D> rooms = zones.stream()
            .filter(zone -> zone.type.equals("classroom") || zone.type.equals("amphi"))
            .toList();

        for (int i = 0; i < count; i++) {
            int floor = i % FLOOR_COUNT;
            List<Zone3D> floorRooms = rooms.stream().filter(zone -> zone.floor == floor).toList();
            Zone3D room = floorRooms.get((i / FLOOR_COUNT) % floorRooms.size());
            Sphere node = new Sphere(10);
            node.setMaterial(material(i % 2 == 0 ? "#2e7de1" : "#15a084"));
            node.setTranslateX(room.x + (random.nextDouble() - 0.5) * room.w * 0.45);
            node.setTranslateY(-28);
            node.setTranslateZ(room.z + (random.nextDouble() - 0.5) * room.d * 0.45);

            Agent3D agent = new Agent3D(node, floorGroups.get(floor), floor, buildRoute(node.getTranslateX(), node.getTranslateZ(), floor));
            agent.speed = 42 + random.nextDouble() * 28;
            floorGroups.get(floor).getChildren().add(node);
            agents.add(agent);
            drawPath(agent);
        }
    }

    private List<Point3D> buildRoute(double x, double z, int floor) {
        Zone3D exit = zones.stream()
            .filter(zone -> zone.floor == floor && zone.type.equals("exit"))
            .findFirst()
            .orElse(null);

        double exitX = exit == null ? 270 : exit.x;
        double exitZ = exit == null ? 0 : exit.z;
        return new ArrayList<>(List.of(
            new Point3D(x, z),
            new Point3D(x, 0),
            new Point3D(exitX - 45, 0),
            new Point3D(exitX + 40, exitZ)
        ));
    }

    private void drawPath(Agent3D agent) {
        List<Point3D> route = agent.route;
        for (int i = 0; i < route.size() - 1; i++) {
            Point3D a = route.get(i);
            Point3D b = route.get(i + 1);
            Cylinder segment = line(a.x, a.z, b.x, b.z, "#e1b145");
            segment.setOpacity(0.45);
            agent.floorGroup.getChildren().add(segment);
            paths.add(segment);
        }
    }

    private Cylinder line(double x1, double z1, double x2, double z2, String color) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double length = Math.hypot(dx, dz);
        Cylinder line = new Cylinder(3, length);
        line.setMaterial(material(color));
        line.setTranslateX((x1 + x2) / 2.0);
        line.setTranslateY(-22);
        line.setTranslateZ((z1 + z2) / 2.0);
        line.setRotationAxis(Rotate.Z_AXIS);
        line.setRotate(90);
        line.getTransforms().add(new Rotate(Math.toDegrees(Math.atan2(dz, dx)), Rotate.Y_AXIS));
        return line;
    }

    private void stepAgents(double delta) {
        double speedFactor = speedSlider.getValue();
        for (Agent3D agent : agents) {
            if (agent.evacuated || agent.floor != activeFloor) continue;
            if (agent.targetIndex >= agent.route.size()) {
                agent.evacuated = true;
                agent.node.setVisible(false);
                continue;
            }

            Point3D target = agent.route.get(agent.targetIndex);
            double dx = target.x - agent.node.getTranslateX();
            double dz = target.z - agent.node.getTranslateZ();
            double distance = Math.hypot(dx, dz);
            double step = delta * agent.speed * speedFactor;
            if (distance <= step) {
                agent.node.setTranslateX(target.x);
                agent.node.setTranslateZ(target.z);
                agent.targetIndex++;
            } else {
                agent.node.setTranslateX(agent.node.getTranslateX() + dx / distance * step);
                agent.node.setTranslateZ(agent.node.getTranslateZ() + dz / distance * step);
            }
        }
        updateStats();
    }

    private void randomFire() {
        if (fireNode != null && fireZone != null) {
            floorGroups.get(fireZone.floor).getChildren().remove(fireNode);
        }

        List<Zone3D> candidates = zones.stream()
            .filter(zone -> zone.floor == activeFloor && !zone.type.equals("exit"))
            .toList();
        if (candidates.isEmpty()) return;

        fireZone = candidates.get(random.nextInt(candidates.size()));
        fireNode = new Sphere(18);
        fireNode.setMaterial(material("#e34a2f"));
        fireNode.setTranslateX(fireZone.x);
        fireNode.setTranslateY(-50);
        fireNode.setTranslateZ(fireZone.z);
        floorGroups.get(activeFloor).getChildren().add(fireNode);

        titleLbl.setText("Incendie détecté");
        detailLbl.setText("Départ de feu: " + fireZone.name + ". Les agents suivent le chemin vers la sortie.");
        fireLbl.setText(fireZone.name);
        running = true;
    }

    private void animateFire(long now) {
        if (fireNode == null) return;
        double pulse = 1.0 + Math.sin(now / 120_000_000.0) * 0.16;
        fireNode.setScaleX(pulse);
        fireNode.setScaleY(pulse);
        fireNode.setScaleZ(pulse);
    }

    private void setActiveFloor(int floor) {
        activeFloor = floor;
        for (int i = 0; i < floorGroups.size(); i++) {
            floorGroups.get(i).setVisible(i == floor);
        }
        titleLbl.setText("Vue " + floorLabel(floor));
        detailLbl.setText("Les éléments de cet étage sont affichés.");
        updateStats();
    }

    private void handleSceneClick() {
        if (activeTool.equals("select")) {
            titleLbl.setText("Vue " + floorLabel(activeFloor));
            detailLbl.setText("Clique sur Démarrer ou ajoute un élément.");
            return;
        }

        if (activeTool.equals("agent")) {
            Zone3D firstRoom = zones.stream()
                .filter(zone -> zone.floor == activeFloor && (zone.type.equals("classroom") || zone.type.equals("amphi")))
                .findFirst()
                .orElse(null);
            if (firstRoom != null) {
                agentCountSlider.setValue(agentCountSlider.getValue() + 1);
            }
        } else {
            double x = -220 + random.nextDouble() * 340;
            double z = -20 + random.nextDouble() * 210;
            addZone(floorGroups.get(activeFloor), "Nouvel élément", activeTool, activeFloor,
                x, z, activeTool.equals("corridor") ? 150 : 90, activeTool.equals("corridor") ? 40 : 70,
                colorForTool(activeTool));
        }
        titleLbl.setText("Élément ajouté");
        detailLbl.setText("Placement effectué sur " + floorLabel(activeFloor) + ".");
    }

    private String colorForTool(String tool) {
        return switch (tool) {
            case "amphi" -> "#d7a955";
            case "corridor" -> "#b7c1cb";
            case "exit" -> "#28a56f";
            default -> "#83b7c8";
        };
    }

    private void updateStats() {
        long active = agents.stream().filter(agent -> agent.floor == activeFloor && !agent.evacuated).count();
        long evacuated = agents.stream().filter(agent -> agent.floor == activeFloor && agent.evacuated).count();
        activeLbl.setText(String.valueOf(active));
        evacuatedLbl.setText(String.valueOf(evacuated));
        if (fireZone == null || fireZone.floor != activeFloor) {
            fireLbl.setText("Aucun");
        }
    }

    private VBox section(String title, Node... nodes) {
        Label label = new Label(title);
        label.setFont(Font.font("Sans", FontWeight.BOLD, 13));
        label.setTextFill(Color.web("#4d5d6b"));
        VBox box = new VBox(10);
        box.getChildren().add(label);
        box.getChildren().addAll(nodes);
        box.setPadding(new Insets(14, 0, 0, 0));
        box.setStyle("-fx-border-color:#d9e0e7;-fx-border-width:1 0 0 0;");
        return box;
    }

    private HBox labeledSlider(String labelText, Slider slider) {
        Label label = new Label(labelText);
        label.setPrefWidth(62);
        Label value = new Label(String.format("%.1f", slider.getValue()));
        slider.valueProperty().addListener((obs, oldValue, newValue) ->
            value.setText(slider == agentCountSlider ? String.valueOf(newValue.intValue()) : String.format("%.1f", newValue.doubleValue())));
        HBox row = new HBox(10, label, slider, value);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private Label statValue(String value) {
        Label label = new Label(value);
        label.setFont(Font.font("Sans", FontWeight.BOLD, 18));
        label.setTextFill(Color.web("#17212b"));
        return label;
    }

    private VBox statBox(String title, Label value) {
        Label label = new Label(title);
        label.setTextFill(Color.web("#697887"));
        VBox box = new VBox(4, label, value);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color:#f8fafb;-fx-border-color:#d9e0e7;-fx-background-radius:8;-fx-border-radius:8;");
        return box;
    }

    private HBox legend(String dot, String text, String color) {
        Label d = new Label(dot);
        d.setTextFill(Color.web(color));
        Label t = new Label(text);
        t.setTextFill(Color.web("#40505f"));
        return new HBox(6, d, t);
    }

    private Button button(String text, String color) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;-fx-font-size:12px;-fx-padding:9;-fx-background-radius:8;-fx-cursor:hand;");
        return button;
    }

    private Box box(double width, double height, double depth, String color) {
        Box box = new Box(width, height, depth);
        box.setMaterial(material(color));
        return box;
    }

    private PhongMaterial material(String color) {
        PhongMaterial material = new PhongMaterial(Color.web(color));
        material.setSpecularColor(Color.rgb(255, 255, 255, 80));
        return material;
    }

    private String floorLabel(int floor) {
        return floor == 0 ? "RDC" : "Étage " + floor;
    }

    private static class Zone3D {
        final String name;
        final String type;
        final int floor;
        final double x;
        final double z;
        final double w;
        final double d;
        final Group node;

        Zone3D(String name, String type, int floor, double x, double z, double w, double d, Group node) {
            this.name = name;
            this.type = type;
            this.floor = floor;
            this.x = x;
            this.z = z;
            this.w = w;
            this.d = d;
            this.node = node;
        }
    }

    private static class Agent3D {
        final Sphere node;
        final Group floorGroup;
        final int floor;
        final List<Point3D> route;
        int targetIndex = 1;
        double speed = 60;
        boolean evacuated = false;

        Agent3D(Sphere node, Group floorGroup, int floor, List<Point3D> route) {
            this.node = node;
            this.floorGroup = floorGroup;
            this.floor = floor;
            this.route = route;
        }
    }

    private static class Point3D {
        final double x;
        final double z;

        Point3D(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
