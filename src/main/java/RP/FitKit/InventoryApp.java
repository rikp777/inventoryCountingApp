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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
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
    private static final Path SESSION_FILE_PATH = Paths.get(System.getProperty("java.io.tmpdir"), "inventory_session.dat");

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
    private JLabel summaryPalletCountLabel;
    private JLabel summaryTotalQuantityLabel;
    private JLabel summaryUniqueItemsLabel;

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
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        inputPanel.setBackground(COLOR_PANEL_BG);

        JPanel headerPanel = new JPanel(new BorderLayout(0, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(2, 5, 2, 5));

        JLabel titleLabel = new JLabel("Scan Details");
        titleLabel.setFont(new Font("Helvetica", Font.BOLD, 16));
        titleLabel.setForeground(Color.DARK_GRAY);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton helpButton = new JButton("?");
        helpButton.setMargin(new Insets(0, 0, 0, 0));
        helpButton.setFont(new Font("Helvetica", Font.BOLD, 10));
        helpButton.setPreferredSize(new Dimension(18, 18));
        helpButton.setFocusable(false);
        helpButton.addActionListener(e -> showHelpDialog());
        headerPanel.add(helpButton, BorderLayout.EAST);

        JPanel titledInputPanel = new JPanel(new BorderLayout());
        titledInputPanel.setBorder(BorderFactory.createEtchedBorder());
        titledInputPanel.add(headerPanel, BorderLayout.NORTH);
        titledInputPanel.add(inputPanel, BorderLayout.CENTER);


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
        exportButton.setBackground(new Color(144, 238, 144));
        exportButton.setOpaque(true);
        buttonPanel.add(exportButton);

        JButton exportXmlButton = new JButton("Exporteer naar Exact XML");
        exportXmlButton.setFont(FONT_BUTTON);
        exportXmlButton.addActionListener(e -> exportTableToXml());
        exportXmlButton.setBackground(new Color(255, 102, 102));
        exportXmlButton.setOpaque(true);
        buttonPanel.add(exportXmlButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(COLOR_PANEL_BG);
        topPanel.add(titledInputPanel, BorderLayout.CENTER);
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
                        saveTableState();
                        updateSummaryPanel();
                    } catch (NumberFormatException e) {
                        JOptionPane.showMessageDialog(InventoryApp.this, "Voer een geldig getal in voor het aantal.", "Ongeldige Invoer", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    super.setValueAt(aValue, row, column);
                    saveTableState();
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
                    updateSummaryPanel();
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
        loadTableState();
        updateSummaryPanel();
    }

    private void setupBottomPanel() {
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(COLOR_BACKGROUND);
        bottomPanel.add(createSummaryPanel(), BorderLayout.NORTH);

        legendPanel = new JPanel(new GridLayout(2, 2, 15, 5));
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

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        eastPanel.setOpaque(false);

        editedCountLabel = new JLabel();
        editedCountLabel.setFont(new Font("Helvetica", Font.ITALIC, 12));
        updateEditedCount();
        eastPanel.add(editedCountLabel);

        JButton clearSessionButton = new JButton("Sessie Wissen");
        clearSessionButton.setFont(new Font("Helvetica", Font.PLAIN, 12));
        clearSessionButton.setForeground(Color.BLUE);
        clearSessionButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearSessionButton.setBorder(null);
        clearSessionButton.setContentAreaFilled(false);
        clearSessionButton.addActionListener(e -> {
            int confirmation = JOptionPane.showConfirmDialog(
                    this,
                    "Weet je zeker dat je de huidige sessie wilt wissen?\nAlle niet-geëxporteerde data gaat verloren.",
                    "Bevestig Sessie Wissen",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirmation == JOptionPane.YES_OPTION) {
                clearSession();
            }
        });
        eastPanel.add(clearSessionButton);

        bottomPanel.add(eastPanel, BorderLayout.EAST);
    }


    private JPanel createLegendItem(Color color, String text) {
        JPanel itemPanel = new JPanel();
        itemPanel.setLayout(new BoxLayout(itemPanel, BoxLayout.X_AXIS));
        itemPanel.setOpaque(false);
        itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel colorSwatch = new JPanel();
        colorSwatch.setPreferredSize(new Dimension(16, 16));
        colorSwatch.setMaximumSize(new Dimension(16, 16));
        colorSwatch.setBackground(color);
        colorSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JLabel label = new JLabel(text);
        label.setFont(new Font("Helvetica", Font.PLAIN, 12));

        itemPanel.add(colorSwatch);
        itemPanel.add(Box.createRigidArea(new Dimension(5, 0)));
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

        try {
            Long.parseLong(palletId);
        } catch (NumberFormatException e) {
            if (!palletId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Pallet ID moet een geldig getal zijn.", "Ongeldige Invoer", JOptionPane.WARNING_MESSAGE);
                palletIdField.setText("");
            }
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
                        saveTableState();
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
        saveTableState();
        updateSummaryPanel();
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
                saveTableState();
                updateSummaryPanel();
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

    // Here the shit starts
    private void exportTableToXml() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "De tabel is leeg. Er is niets om te exporteren.", "Exportfout", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Exact XML-bestand opslaan");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("XML-bestanden", "xml"));
        fileChooser.setSelectedFile(new File("voorraadtelling.xml"));

        int userSelection = fileChooser.showSaveDialog(this);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getPath().toLowerCase().endsWith(".xml")) {
                fileToSave = new File(fileToSave.getPath() + ".xml");
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                LocalDate today = LocalDate.now();
                String formattedDate = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
                String entryId = String.valueOf(System.currentTimeMillis()).substring(3); // Unique ID

                writer.write("<?xml version=\"1.0\"?>\n");
                writer.write("<eExact xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"eExact-Schema.xsd\">\n");
                writer.write("  <FinEntries>\n");
                writer.write("    <FinEntry entry=\"" + entryId + "\">\n");
                writer.write("      <Journal code=\"090\" type=\"M\">\n");
                writer.write("        <Description>Memoriaal JR</Description>\n");
                writer.write("      </Journal>\n");

                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    String palletId = escapeXml(tableModel.getValueAt(row, 0));
                    String itemCode = escapeXml(extractItemCode(tableModel.getValueAt(row, 1)));
                    String quantity = escapeXml(tableModel.getValueAt(row, 2));

                    // Credit Line
                    writer.write("      <FinEntryLine number=\"" + ((row * 2) + 1) + "\" type=\"N\" subtype=\"G\" code=\"1\" linecode=\"B\" transactiontype=\"100\">\n");
                    writer.write("        <Date>" + formattedDate + "</Date>\n");
                    writer.write("        <GLAccount code=\"3999\"></GLAccount>\n");
                    writer.write("        <Description>Omschrijving van telling</Description>\n");
                    writer.write("        <Costcenter code=\"001CC001\"></Costcenter>\n");
                    writer.write("        <Item code=\"" + itemCode + "\"></Item>\n");
                    writer.write("        <Warehouse code=\"1\"></Warehouse>\n");
                    writer.write("        <Project code=\"\"></Project>\n");
                    writer.write("        <Quantity>" + quantity + "</Quantity>\n");
                    writer.write("        <Amount>\n");
                    writer.write("          <Currency code=\"EUR\"/>\n");
                    writer.write("          <Debit>0</Debit>\n");
                    writer.write("          <Credit>0</Credit>\n");
                    writer.write("          <VAT code=\"0\" type=\"B\" vattype=\"N\"></VAT>\n");
                    writer.write("        </Amount>\n");
                    writer.write("      </FinEntryLine>\n");

                    // Debit Line
                    writer.write("      <FinEntryLine number=\"" + ((row * 2) + 2) + "\" type=\"N\" subtype=\"G\" code=\"2\" linecode=\"B\" transactiontype=\"100\">\n");
                    writer.write("        <Date>" + formattedDate + "</Date>\n");
                    writer.write("        <GLAccount code=\"3550\"></GLAccount>\n");
                    writer.write("        <Description>Omschrijving van telling</Description>\n");
                    writer.write("        <Costcenter code=\"001CC001\"></Costcenter>\n");
                    writer.write("        <Item code=\"" + itemCode + "\"></Item>\n");
                    writer.write("        <Warehouse code=\"1\"></Warehouse>\n");
                    writer.write("        <Project code=\"\"></Project>\n");
                    writer.write("        <Quantity>-" + quantity + "</Quantity>\n");
                    writer.write("        <Amount>\n");
                    writer.write("          <Currency code=\"EUR\"/>\n");
                    writer.write("          <Debit>0</Debit>\n");
                    writer.write("          <Credit>0</Credit>\n");
                    writer.write("          <VAT code=\"0\" type=\"B\" vattype=\"N\"></VAT>\n");
                    writer.write("        </Amount>\n");
                    writer.write("        <FinReferences TransactionOrigin=\"N\">\n");
                    writer.write("          <YourRef>" + palletId + "</YourRef>\n");
                    writer.write("          <DocumentDate>" + formattedDate + "</DocumentDate>\n");
                    writer.write("          <ReportDate>" + formattedDate + "</ReportDate>\n");
                    writer.write("        </FinReferences>\n");
                    writer.write("      </FinEntryLine>\n");
                }

                writer.write("    </FinEntry>\n");
                writer.write("  </FinEntries>\n");
                writer.write("</eExact>\n");

                JOptionPane.showMessageDialog(this, "De gegevens zijn succesvol geëxporteerd naar " + fileToSave.getName(), "Export succesvol", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Fout bij het exporteren van de gegevens: " + ex.getMessage(), "Exportfout", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private String extractItemCode(Object articleNameObj) {
        if (articleNameObj == null) {
            return "";
        }
        String articleName = articleNameObj.toString();
        int startIndex = articleName.lastIndexOf('(');
        int endIndex = articleName.lastIndexOf(')');

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return articleName.substring(startIndex + 1, endIndex);
        }
        return "";
    }


    private String escapeXml(Object obj) {
        if (obj == null) {
            return "";
        }
        String text = obj.toString();
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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

    private JPanel createSummaryPanel() {
        JPanel summaryPanel = new JPanel(new GridLayout(1, 3, 20, 5));
        summaryPanel.setOpaque(false);
        summaryPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEmptyBorder(0, 5, 5, 5), "Sessie Overzicht",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                new Font("Helvetica", Font.BOLD, 14), Color.DARK_GRAY));

        summaryPalletCountLabel = new JLabel("Gescande Pallets: 0");
        summaryPalletCountLabel.setFont(FONT_LABEL);
        summaryTotalQuantityLabel = new JLabel("Totaal Aantal Stuks: 0");
        summaryTotalQuantityLabel.setFont(FONT_LABEL);
        summaryUniqueItemsLabel = new JLabel("Unieke Artikelen: 0");
        summaryUniqueItemsLabel.setFont(FONT_LABEL);

        summaryPanel.add(summaryPalletCountLabel);
        summaryPanel.add(summaryTotalQuantityLabel);
        summaryPanel.add(summaryUniqueItemsLabel);

        return summaryPanel;
    }
    private void updateSummaryPanel() {
        int palletCount = tableModel.getRowCount();
        long totalQuantity = 0;
        Set<String> uniqueItems = new HashSet<>();

        for (int row = 0; row < palletCount; row++) {
            try {
                totalQuantity += Long.parseLong(tableModel.getValueAt(row, 2).toString());
            } catch (NumberFormatException e) {
                // Ignore rows with invalid quantity for summary calculation
            }
            uniqueItems.add(tableModel.getValueAt(row, 1).toString());
        }

        summaryPalletCountLabel.setText("Gescande Pallets: " + palletCount);
        summaryTotalQuantityLabel.setText("Totaal Aantal Stuks: " + totalQuantity);
        summaryUniqueItemsLabel.setText("Unieke Artikelen: " + uniqueItems.size());
    }


    private void clearSession() {
        try {
            Files.deleteIfExists(SESSION_FILE_PATH);
            tableModel.setRowCount(0);
            editedPalletIds.clear();
            updateEditedCount();
            updateSummaryPanel();
            lastInsertedRow = -1;
            resetForNextScan();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Kon het sessiebestand niet opschonen.", "Sessiefout", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void saveTableState() {
        try (BufferedWriter writer = Files.newBufferedWriter(SESSION_FILE_PATH)) {
            String editedIds = String.join(",", editedPalletIds);
            writer.write("EDITED_IDS:" + editedIds);
            writer.newLine();

            for (int row = 0; row < tableModel.getRowCount(); row++) {
                writer.write("ROW_START");
                writer.newLine();
                writer.write(String.valueOf(tableModel.getValueAt(row, 0))); // Pallet ID
                writer.newLine();
                writer.write(String.valueOf(tableModel.getValueAt(row, 1))); // Artikel
                writer.newLine();
                writer.write(String.valueOf(tableModel.getValueAt(row, 2))); // Aantal
                writer.newLine();
                writer.write(String.valueOf(tableModel.getValueAt(row, 3))); // Notities
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadTableState() {
        if (!Files.exists(SESSION_FILE_PATH)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(SESSION_FILE_PATH);
            if (lines.isEmpty()) {
                return;
            }

            String header = lines.get(0);
            if (header.startsWith("EDITED_IDS:")) {
                String idString = header.substring("EDITED_IDS:".length());
                if (!idString.isEmpty()) {
                    String[] ids = idString.split(",");
                    editedPalletIds.addAll(Arrays.asList(ids));
                }
            }

            int i = 1;
            while (i < lines.size()) {
                if (lines.get(i).equals("ROW_START") && i + 4 < lines.size()) {
                    String palletId = lines.get(i + 1);
                    String artikel = lines.get(i + 2);
                    int aantal = Integer.parseInt(lines.get(i + 3));
                    String notities = lines.get(i + 4);
                    tableModel.addRow(new Object[]{palletId, artikel, aantal, notities});
                    i += 5;
                } else {
                    i++;
                }
            }
            updateEditedCount();
            updateSummaryPanel();
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Kon de vorige sessie niet herstellen.", "Sessiefout", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showHelpDialog() {
        String helpMessage = """
                <html>
                <body style='width: 350px;'>
                  <h2>Uitleg Voorraadtelling</h2>
                  <p>Dit programma is ontworpen om een <strong>CSV-tellijst</strong> te genereren. Deze lijst kan vervolgens direct in <strong>Exact</strong> worden geïmporteerd.</p>
                  
                  <h3>Belangrijk: Aantallen Aanpassen</h3>
                  <p>Wanneer u een pallet scant, wordt het veld 'Ingevoerd Aantal' automatisch gevuld met het aantal zoals het oorspronkelijk is geregistreerd bij binnenkomst.</p>
                  
                  <p>Als een pallet is <strong>aangeslagen</strong> (d.w.z. deels gebruikt), is het cruciaal dat u dit standaard aantal <strong>handmatig aanpast</strong> naar de daadwerkelijke hoeveelheid die zich nu op de pallet bevindt.</p>
                </body>
                </html>
                """;

        JOptionPane.showMessageDialog(this,
                helpMessage,
                "Uitleg",
                JOptionPane.INFORMATION_MESSAGE);
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