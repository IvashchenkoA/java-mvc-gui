package views;

import controllers.Controller;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class ModelGUI extends JFrame {

    private final JList<String> modelList;
    private final JList<String> dataList;
    private final JTable resultTable;

    private Controller controller;

    public ModelGUI() {
        modelList = new JList<>(getModelNames());
        dataList = new JList<>(getDataFileNames());
        resultTable = new JTable();
        JButton runModelButton = new JButton("Run Model");
        JButton runScriptButton = new JButton("Run Script from File");
        JButton adhocScriptButton = new JButton("Create and Run Ad hoc Script");

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(new JLabel("Select model and data"), BorderLayout.NORTH);
        JPanel selectPanel = new JPanel(new GridLayout(1,2));
        selectPanel.add(new JScrollPane(modelList));
        selectPanel.add(new JScrollPane(dataList));
        leftPanel.add(selectPanel,BorderLayout.CENTER);
        leftPanel.add(runModelButton, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1,2));
        buttonPanel.add(runScriptButton);
        buttonPanel.add(adhocScriptButton);
        rightPanel.add(buttonPanel, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.25;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(leftPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.75;
        panel.add(rightPanel, gbc);
        add(panel);

        runModelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runModel();
            }
        });

        runScriptButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runScriptFromFile();
            }
        });

        adhocScriptButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runAdhocScript();
            }
        });
        setSize(800, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private String[] getModelNames() {
        List<String> classNames = new ArrayList<>();
        try {
            String packageName = "models";
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(packageName.replace('.', '/'));
            while (resources.hasMoreElements()) {
                File file = new File(resources.nextElement().toURI());
                if (file.isDirectory()) {
                    for (File classFile : file.listFiles()) {
                        if (classFile.isFile() && classFile.getName().endsWith(".class")) {
                            String className =  classFile.getName().replace(".class", "");
                            classNames.add(className);
                        }
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return classNames.toArray(new String[0]);
    }

    private String[] getDataFileNames() {
        String dataDir = System.getProperty("user.home") + "/Modeling/data/";
        File dataFolder = new File(dataDir);
        if (!dataFolder.exists() || !dataFolder.isDirectory()) {
            return new String[0];
        }
        return dataFolder.list();
    }
    private void runModel() {
        String modelName = modelList.getSelectedValue();
        String dataFileName = dataList.getSelectedValue();
        String dataFilePath = System.getProperty("user.home") + "/Modeling/data/" +  dataFileName;
        controller = new Controller("models." + modelName);
        controller.readDataFrom(dataFilePath).runModel();
        updateTable();
    }

    private void runScriptFromFile() {
        String scriptDir = System.getProperty("user.home") + "/Modeling/scripts/";
        String scriptFileName = chooseScriptFile();
        if (scriptFileName != null) {
            controller.runScriptFromFile(scriptDir + scriptFileName);
            updateTable();
        }
    }

    private String chooseScriptFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home") + "/Modeling/scripts/"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getName();
        }
        return null;
    }

    private void runAdhocScript() {
        JTextArea adhocScriptTextArea = new JTextArea();
        adhocScriptTextArea.setRows(10);
        adhocScriptTextArea.setColumns(30);
        JScrollPane scriptScrollPane = new JScrollPane(adhocScriptTextArea);
        int result = JOptionPane.showOptionDialog(
                null, scriptScrollPane, "Ad hoc Script Input",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, null, null);
        if (result == JOptionPane.OK_OPTION) {
            String script = adhocScriptTextArea.getText();
            if (script != null && !script.isEmpty()) {
                if (controller == null) {
                    this.runModel();
                }
                controller.runScript(script);
                updateTable();
            }
        }

}

    private void updateTable() {
        String results = controller.getResultsAsTsv();
        String[] rows = results.split("\n");
        rows[0] = rows[0].replace("LATA", "");
        String[] columns = rows[0].split("\t");
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        resultTable.setModel(model);
        for (int i = 1; i < rows.length; i++) {
            String[] cols = rows[i].split("\t");
            String name = cols[0];
            cols = Arrays.copyOfRange(cols, 1, cols.length);
            Object[] rowData = new Object[cols.length + 1];
            rowData[0] = name;
            for (int j = 0; j < cols.length; j++) {
                try {
                    double formattedValue = formatValues(Double.parseDouble(cols[j]));
                    rowData[j + 1] = formattedValue;
                } catch (NumberFormatException e) {
                    rowData[j + 1] = cols[j];
                }
            }
            model.addRow(rowData);
            for (int col = 0; col < model.getColumnCount(); col++) {
                TableCellRenderer renderer = new AlignmentCellRenderer();
                resultTable.getColumnModel().getColumn(col).setCellRenderer(renderer);
            }
        }
    }

    private double formatValues(double value){
        String formatted;
        if (value >= 1 && value <= 100) {
            formatted = String.format("%.2f", value);
        } else if (value > 100) {
            formatted = String.format("%.1f", value);
        } else {
            formatted = String.format("%.3f", value);
        }
        return Double.parseDouble(formatted);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ModelGUI().setVisible(true);
            }
        });
    }
    private static class AlignmentCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component cellComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (column == 0) {
                    setHorizontalAlignment(SwingConstants.LEFT);
                } else {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                }
            return cellComponent;
        }
    }
}
