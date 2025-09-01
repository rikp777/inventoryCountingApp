package RP.FitKit;

import io.github.cdimascio.dotenv.Dotenv;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.swing.Timer;

public class InventoryApp extends JFrame {
    private static final Font FONT_HEADER = new Font("Helvetica", Font.BOLD, 24);
    private static final Font FONT_LABEL = new Font("Helvetica", Font.PLAIN, 14);
    private static final Font FONT_BUTTON = new Font("Helvetica", Font.BOLD, 14);
    private static final Font FONT_INFO = new Font( "Helvetica", Font.BOLD, 14);
    private static final Color COLOR_INFO = new Color(0, 120, 215);
    private static final Color COLOR_BACKGROUND = new Color(245, 245, 245);
    private static final Color COLOR_TABLE_HEADER = new Color(220, 220, 220);
    private static final Color COLOR_TABLE_ALT_ROW = new Color(235, 245, 255);
    private static final Color COLOR_HIGHLIGHT = new Color(255, 255, 204);
    private static final Color COLOR_EDITED = new Color(255, 224, 178);
    private static final Color COLOR_PANEL_BG = Color.WHITE;

    private Connection dbConnection;

    private JPanel startPanel;
    private JPanel mainPanel;
    private JTextField palletIdField;
    private JLabel quantityLabel;
    private JLabel productNameLabel;
    private DefaultTableModel tableModel;
    private JTable inventoryTable;
    private JButton saveButton;
    private String displayedPalletId;
    private int lastInsertedRow = -1;
    private Set<String> editedPalletIds = new HashSet<>();
    private JLabel editedCountLabel;
    private JButton startButton;
    private JPanel loadingPanel;
    private JLabel slowConnectionLabel;
    private Timer connectionTimer;
    private Timer discoTimer;
    private boolean isDiscoMode = false;
    private JPanel bottomPanel;
    private JPanel legendPanel;

    public InventoryApp() {
        setTitle("Voorraadtelling Prototype (Java)");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClosing();
            }
        });

        createStartScreen();
        createLoadingScreen();
        setContentPane(startPanel);
    }

    private void createLoadingScreen() {
        loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setBackground(COLOR_BACKGROUND);
        JLabel loadingLabel = new JLabel("Verbinden met de database...");
        loadingLabel.setFont(FONT_HEADER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        slowConnectionLabel = new JLabel(" ");
        slowConnectionLabel.setFont(new Font("Helvetica", Font.ITALIC, 14));
        slowConnectionLabel.setForeground(Color.GRAY);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(10, 0, 10, 0);

        loadingPanel.add(loadingLabel, gbc);
        loadingPanel.add(progressBar, gbc);

        gbc.insets = new Insets(5, 0, 0, 0);
        loadingPanel.add(slowConnectionLabel, gbc);

    }

    private void createStartScreen() {
        startPanel = new JPanel(new GridBagLayout());
        startPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        startPanel.setBackground(COLOR_BACKGROUND);

        JLabel startLabel = new JLabel("Klaar om de voorraad te tellen?");
        startLabel.setFont(FONT_HEADER);

        startButton = new JButton("Nieuwe Voorraadtelling Starten");
        startButton.setFont(FONT_BUTTON);
        startButton.setPreferredSize(new Dimension(300, 50));
        startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startButton.addActionListener(e -> initiateConnection());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(10, 0, 10, 0);

        startPanel.add(startLabel, gbc);
        startPanel.add(startButton, gbc);
    }

    private void initiateConnection() {
        setContentPane(loadingPanel);
        slowConnectionLabel.setText(" ");
        revalidate();
        repaint();

        connectionTimer = new Timer(4000, e -> {
            slowConnectionLabel.setText("Dit duurt langer dan verwacht, er is mogelijk een probleem...");
        });
        connectionTimer.setRepeats(false);

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return connectToDatabase();
            }

            @Override
            protected void done() {
                connectionTimer.stop();
                try {
                    boolean success = get();
                    if (success) {
                        createMainScreen();
                        setContentPane(mainPanel);
                        revalidate();
                        repaint();
                        SwingUtilities.invokeLater(() -> palletIdField.requestFocusInWindow());
                    } else {
                        JOptionPane.showMessageDialog(InventoryApp.this,
                                "Kon geen verbinding maken met de database. Controleer de instellingen en netwerkverbinding.",
                                "Database Fout", JOptionPane.ERROR_MESSAGE);
                        setContentPane(startPanel);
                        revalidate();
                        repaint();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(InventoryApp.this,
                            "Een onverwachte fout is opgetreden tijdens het verbinden: " + ex.getMessage(),
                            "Verbindingsfout", JOptionPane.ERROR_MESSAGE);
                    setContentPane(startPanel);
                    revalidate();
                    repaint();
                }
            }
        };

        connectionTimer.start();
        worker.execute();
    }

    private void createMainScreen() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(COLOR_BACKGROUND);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Scan Details",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Helvetica", Font.BOLD, 16), Color.DARK_GRAY));
        inputPanel.setBackground(COLOR_PANEL_BG);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel palletIdLabel = new JLabel("Scan Pallet ID:");
        palletIdLabel.setFont(FONT_LABEL);
        inputPanel.add(palletIdLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        palletIdField = new JTextField();
        palletIdField.setFont(FONT_LABEL);
        palletIdField.addActionListener(e -> fetchProductInfo());
        inputPanel.add(palletIdField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JLabel articleLabel = new JLabel("Artikel:");
        articleLabel.setFont(FONT_LABEL);
        inputPanel.add(articleLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        productNameLabel = new JLabel("--- Wacht op scan ---");
        productNameLabel.setFont(FONT_INFO);
        productNameLabel.setForeground(COLOR_INFO);
        inputPanel.add(productNameLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        JLabel quantityDescLabel = new JLabel("Aantal:");
        quantityDescLabel.setFont(FONT_LABEL);
        inputPanel.add(quantityDescLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        quantityLabel = new JLabel("--- Wacht op scan ---");
        quantityLabel.setFont(FONT_INFO);
        quantityLabel.setForeground(COLOR_INFO);
        inputPanel.add(quantityLabel, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(COLOR_PANEL_BG);
        saveButton = new JButton("Opslaan");
        saveButton.setFont(FONT_BUTTON);
        saveButton.addActionListener(e -> saveItem());
        buttonPanel.add(saveButton);

        JButton deleteButton = new JButton("Verwijder selectie");
        deleteButton.setFont(FONT_BUTTON);
        deleteButton.addActionListener(e -> deleteSelectedItem());
        buttonPanel.add(deleteButton);

        JButton exportButton = new JButton("Exporteer naar CSV");
        exportButton.setFont(FONT_BUTTON);
        exportButton.addActionListener(e -> exportTableToCsv());
        buttonPanel.add(exportButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(COLOR_PANEL_BG);
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        String[] columnNames = {"Pallet ID", "Artikel", "Ingevoerd Aantal", "Notities"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2 || column == 3;
            }

            @Override
            public void setValueAt(Object aValue, int row, int column) {
                if (column == 2) {
                    try {
                        int newValue = Integer.parseInt(aValue.toString());

                        if (newValue <= 0) {
                            JOptionPane.showMessageDialog(InventoryApp.this, "Aantal moet een positief getal zijn.", "Ongeldige Invoer", JOptionPane.WARNING_MESSAGE);
                            return;
                        }

                        if (newValue > 500) {
                            int confirmation = JOptionPane.showConfirmDialog(
                                    InventoryApp.this,
                                    "Het ingevoerde aantal (" + newValue + ") is erg hoog. Weet je het zeker?",
                                    "Bevestig Hoog Aantal",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE
                            );
                            if (confirmation != JOptionPane.YES_OPTION) {
                                return;
                            }
                        }

                        super.setValueAt(aValue, row, column);
                        String palletId = (String) getValueAt(row, 0);
                        editedPalletIds.add(palletId);
                        updateEditedCount();

                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(InventoryApp.this, "Voer een geldig getal in voor het aantal.", "Ongeldige Invoer", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    super.setValueAt(aValue, row, column);
                }
            }
        };
        inventoryTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (isRowSelected(row)) {
                    return c;
                }
                String palletId = (String) getValueAt(row, 0);

                if (row == lastInsertedRow) {
                    c.setBackground(COLOR_HIGHLIGHT);
                } else if (column == 2 && editedPalletIds.contains(palletId)) {
                    c.setBackground(COLOR_EDITED);
                } else {
                    c.setBackground(row % 2 == 0 ? COLOR_TABLE_ALT_ROW : Color.WHITE);
                }
                return c;
            }

        };
        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int column = e.getColumn();
                if (column == 2) {
                    String palletId = (String) tableModel.getValueAt(row, 0);
                    editedPalletIds.add(palletId);
                    updateEditedCount();
                }
            }
        });

        inventoryTable.setRowHeight(28);
        inventoryTable.setFont(FONT_LABEL);
        inventoryTable.getTableHeader().setFont(FONT_BUTTON);
        inventoryTable.getTableHeader().setBackground(COLOR_TABLE_HEADER);
        inventoryTable.setGridColor(COLOR_TABLE_HEADER);
        JScrollPane scrollPane = new JScrollPane(inventoryTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_TABLE_HEADER));


        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        setupBottomPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupBottomPanel() {
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(COLOR_BACKGROUND);

        legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        legendPanel.setBackground(COLOR_BACKGROUND);
        legendPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(), "Legenda",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Helvetica", Font.BOLD, 14), Color.DARK_GRAY));

        legendPanel.add(createLegendItem(COLOR_HIGHLIGHT, "Laatst toegevoegd item"));
        legendPanel.add(createLegendItem(COLOR_EDITED, "Aantal handmatig gewijzigd"));
        legendPanel.add(createLegendItem(inventoryTable.getSelectionBackground(), "Geselecteerd voor verwijderen/bewerken"));
        legendPanel.add(createLegendItem(COLOR_TABLE_ALT_ROW, "Afwisselende rijkleur"));

        bottomPanel.add(legendPanel, BorderLayout.CENTER);

        editedCountLabel = new JLabel();
        editedCountLabel.setFont(new Font("Helvetica", Font.ITALIC, 12));
        editedCountLabel.setBorder(new EmptyBorder(0, 0, 0, 10));
        updateEditedCount();
        bottomPanel.add(editedCountLabel, BorderLayout.EAST);
    }

    private JPanel createLegendItem(Color color, String text) {
        JPanel itemPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        itemPanel.setOpaque(false);

        JPanel colorSwatch = new JPanel();
        colorSwatch.setPreferredSize(new Dimension(16, 16));
        colorSwatch.setBackground(color);
        colorSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JLabel label = new JLabel(text);
        label.setFont(new Font("Helvetica", Font.PLAIN, 12));

        itemPanel.add(colorSwatch);
        itemPanel.add(label);

        return itemPanel;
    }

    private void updateEditedCount() {
        int count = editedPalletIds.size();
        if (count == 0) {
            editedCountLabel.setText("");
        } else if (count == 1) {
            editedCountLabel.setText("In 1 rij is het aantal gewijzigd.");
        } else {
            editedCountLabel.setText("In " + count + " rijen is het aantal gewijzigd.");
        }
    }

    private boolean connectToDatabase() {
        try {
            Dotenv dotenv = Dotenv.load();
            String dbServer = dotenv.get("DB_SERVER");
            String dbPort = dotenv.get("DB_PORT");
            String dbDatabase = dotenv.get("DB_DATABASE");
            String dbUser = dotenv.get("DB_USER");
            String dbPassword = dotenv.get("DB_PASSWORD");

            String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                    dbServer, dbPort, dbDatabase);


            dbConnection = DriverManager.getConnection(url, dbUser, dbPassword);
            System.out.println("Databaseverbinding succesvol.");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Kon de configuratie niet laden. Zorg ervoor dat het .env-bestand bestaat.\nFout: " + e.getMessage(),
                    "Configuratiefout", JOptionPane.ERROR_MESSAGE);
            return false;
        }

    }

    private void fetchProductInfo() {
        if (lastInsertedRow != -1) {
            lastInsertedRow = -1;
            inventoryTable.repaint();
        }

        String palletId = palletIdField.getText().trim();

        // yes shut up let me be
        if (palletId.equalsIgnoreCase("magic")) {
            isDiscoMode = !isDiscoMode;
            if (isDiscoMode) {
                startDiscoMode();
            } else {
                stopDiscoMode();
            }
            palletIdField.setText("");
            return;
        }

        if (displayedPalletId != null && !displayedPalletId.isEmpty() && !productNameLabel.getText().contains("---")) {
            if (!isPalletIdInTable(displayedPalletId)) {
                String productName = productNameLabel.getText();
                String quantityStr = quantityLabel.getText();

                try {
                    int quantity = Integer.parseInt(quantityStr);
                    if (quantity > 0) {
                        tableModel.addRow(new Object[]{displayedPalletId, productName, quantity, ""});
                        lastInsertedRow = tableModel.getRowCount() - 1;
                    }
                } catch (NumberFormatException ex) {

                }
            }
        }

        if (palletId.isEmpty()) {
            resetForNextScan();
            return;
        }

        String query = """
                SELECT oa.full_name, apl.article_quantity FROM article_pallet_labels apl 
                JOIN original_articles oa ON oa.id = apl.original_article_id 
                WHERE apl.distribution_count = ?
                """;

        try (PreparedStatement pstmt = dbConnection.prepareStatement(query)) {
            pstmt.setString(1, palletId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                productNameLabel.setText(rs.getString("full_name"));
                quantityLabel.setText(rs.getString("article_quantity"));
                palletIdField.requestFocusInWindow();
                palletIdField.selectAll();
                displayedPalletId = palletId;
            } else {
                productNameLabel.setText("--- ARTIKEL NIET GEVONDEN ---");
                quantityLabel.setText("--- Wacht op scan ---");
                palletIdField.setText("");
                displayedPalletId = null;
            }
        } catch (SQLException e) {
            productNameLabel.setText("--- DATABASEFOUT ---");
            quantityLabel.setText("---");
            e.printStackTrace();
            displayedPalletId = null;
        }
    }


    private void saveItem() {
        String palletId = displayedPalletId;
        String productName = productNameLabel.getText();
        String quantityStr = quantityLabel.getText().trim();

        if (palletId == null || palletId.isEmpty() || productName.contains("---")) {
            JOptionPane.showMessageDialog(this, "Scan eerst een geldig Pallet ID.", "Ongeldige Invoer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isPalletIdInTable(palletId)) {
            JOptionPane.showMessageDialog(this, "Pallet ID " + palletId + " is al gescand.", "Dubbele Invoer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quantity;

        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Voer een geldig, positief getal in voor het aantal.", "Ongeldige Invoer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        tableModel.addRow(new Object[]{palletId, productName, quantity, ""});
        lastInsertedRow = tableModel.getRowCount() - 1;
        resetForNextScan();
    }

    private void deleteSelectedItem() {
        int[] selectedRows = inventoryTable.getSelectedRows();
        if (selectedRows.length > 0) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Weet je zeker dat je de " + selectedRows.length + " geselecteerde rijen wilt verwijderen?",
                    "Bevestig verwijderen",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                Arrays.sort(selectedRows);
                for (int i = selectedRows.length - 1; i >= 0; i--) {
                    int rowToDelete = selectedRows[i];

                    String palletId = (String) tableModel.getValueAt(rowToDelete, 0);
                    editedPalletIds.remove(palletId);

                    tableModel.removeRow(rowToDelete);
                    if (rowToDelete == lastInsertedRow) {
                        lastInsertedRow = -1;
                    } else if (rowToDelete < lastInsertedRow) {
                        lastInsertedRow--;
                    }
                }
                updateEditedCount();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Selecteer een of meerdere rijen om te verwijderen.", "Geen selectie", JOptionPane.WARNING_MESSAGE);
        }
    }


    private boolean isPalletIdInTable(String palletId) {
        if (palletId == null) return false;
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (palletId.equals(tableModel.getValueAt(row, 0))) {
                return true;
            }
        }
        return false;
    }

    private void exportTableToCsv() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "De tabel is leeg. Er is niets om te exporteren.", "Exportfout", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("CSV-bestand opslaan");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV-bestanden", "csv"));
        fileChooser.setSelectedFile(new File("voorraadtelling.csv"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getPath().toLowerCase().endsWith(".csv")) {
                fileToSave = new File(fileToSave.getPath() + ".csv");
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                for (int i = 0; i < tableModel.getColumnCount(); i++) {
                    writer.write(tableModel.getColumnName(i));
                    if (i < tableModel.getColumnCount() - 1) {
                        writer.write(",");
                    }
                }
                writer.newLine();

                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    for (int col = 0; col < tableModel.getColumnCount(); col++) {
                        Object value = tableModel.getValueAt(row, col);
                        writer.write(value == null ? "" : value.toString());
                        if (col < tableModel.getColumnCount() - 1) {
                            writer.write(",");
                        }
                    }
                    writer.newLine();
                }
                JOptionPane.showMessageDialog(this, "De gegevens zijn succesvol geëxporteerd naar " + fileToSave.getName(), "Export succesvol", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Fout bij het exporteren van de gegevens: " + ex.getMessage(), "Exportfout", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void resetForNextScan() {
        palletIdField.setText("");
        productNameLabel.setText("--- Wacht op scan ---");
        quantityLabel.setText("--- Wacht op scan ---");
        palletIdField.requestFocusInWindow();
        displayedPalletId = null;
    }

    private void onClosing() {
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
                System.out.println("Databaseverbinding gesloten.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        dispose();
        System.exit(0);
    }

    private void startDiscoMode() {
        if (discoTimer != null && discoTimer.isRunning()) {
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Disco Mode Geactiveerd!",
                "Easter Egg!",
                JOptionPane.INFORMATION_MESSAGE);

        discoTimer = new Timer(200, e -> {
            Random random = new Random();
            Color randomColor = new Color(random.nextFloat(), random.nextFloat(), random.nextFloat());

            mainPanel.setBackground(randomColor);
            bottomPanel.setBackground(randomColor);
            legendPanel.setBackground(randomColor);
        });
        discoTimer.start();
    }

    private void stopDiscoMode() {
        if (discoTimer != null) {
            discoTimer.stop();
        }
        mainPanel.setBackground(COLOR_BACKGROUND);
        bottomPanel.setBackground(COLOR_BACKGROUND);
        legendPanel.setBackground(COLOR_BACKGROUND);
        JOptionPane.showMessageDialog(this,
                "Disco Mode Gedeactiveerd.",
                "Easter Egg!",
                JOptionPane.INFORMATION_MESSAGE);
    }

}