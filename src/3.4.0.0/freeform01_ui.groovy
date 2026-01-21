import groovy.swing.SwingBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.table.*
import javax.swing.event.*
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.ArrayList
import java.util.HashMap
import java.util.List
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback
import com.jdimension.jlawyer.client.settings.ServerSettings

public class freeform01_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    JPanel SCRIPTPANEL = null
    FormPluginCallback callback = null

    JTabbedPane tabbedPane = null
    JPanel settingsPanel = null

    // Settings tab components
    JTable elementsTable = null
    DefaultTableModel tableModel = null
    JComboBox cmbTabSelect = null
    JTextField txtNewTabName = null
    JTextField txtElementId = null
    JComboBox cmbElementType = null
    JTextField txtElementLabel = null
    JTextField txtElementPlaceholder = null
    JTextArea txtElementChoices = null
    JPanel newTabPanel = null

    // Current UI definition
    String currentUiDefinition = null
    boolean uiBuilt = false

    // Supported element types: technical name -> display name
    static final LinkedHashMap<String, String> ELEMENT_TYPE_MAP = [
        'textbox': 'Textfeld (einzeilig)',
        'textarea': 'Textfeld (mehrzeilig)',
        'select': 'Auswahlfeld',
        'checkbox': 'Kontrollkästchen',
        'date': 'Datum',
        'number': 'Ganzzahl',
        'amount': 'Betrag (Dezimalzahl)',
        'separator': 'Trennlinie',
        'section': 'Abschnittsüberschrift',
        'spacer': 'Leerzeile'
    ]

    // Helper methods for type conversion
    private String getDisplayNameForType(String technicalName) {
        return ELEMENT_TYPE_MAP.get(technicalName) ?: technicalName
    }

    private String getTechnicalNameForDisplay(String displayName) {
        for (entry in ELEMENT_TYPE_MAP.entrySet()) {
            if (entry.value == displayName) {
                return entry.key
            }
        }
        return displayName
    }

    int TEXTFIELD_MAXCOLUMNS = 50

    public freeform01_ui() {
        super()
    }

    public String getAsHtml() {
        return GuiLib.getAsHtml(this.SCRIPTPANEL)
    }

    public ArrayList<String> getPlaceHolders(String prefix) {
        ArrayList<String> placeHolders = FormsLib.getPlaceHolders(prefix, this.SCRIPTPANEL)
        return placeHolders
    }

    public Hashtable getPlaceHolderValues(String prefix) {
        Hashtable placeHolders = FormsLib.getPlaceHolderValues(prefix, this.SCRIPTPANEL)
        return placeHolders
    }

    public Hashtable getPlaceHolderDescriptions(String prefix) {
        Hashtable placeHolders = FormsLib.getPlaceHolderDescriptions(prefix, this.SCRIPTPANEL)
        return placeHolders
    }

    public void setPlaceHolderValues(String prefix, Hashtable placeHolderValues) {
        if (!uiBuilt) {
            String uiDef = loadUiDefinition()
            if (uiDef != null && !uiDef.isEmpty()) {
                rebuildDynamicUi(uiDef)
            }
        }
        FormsLib.setPlaceHolderValues(prefix, placeHolderValues, this.SCRIPTPANEL)
    }

    public void setCallback(FormPluginCallback callback) {
        this.callback = callback
    }

    private String loadUiDefinition() {
        String setting = ServerSettings.getInstance().getSetting("forms.freeform01.uidefinition", "")
        if (setting == null || setting.trim().isEmpty()) {
            return getDefaultJsonDefinition()
        }
        return setting
    }

    private String getDefaultJsonDefinition() {
        def defaultDef = [
            tabs: [
                [
                    tabTitle: "Beispieldaten",
                    fields: [
                        [id: "text1", type: "textbox", label: "Textfeld", placeHolder: "TEXTFELD"],
                        [id: "area1", type: "textarea", label: "Mehrzeiliges Textfeld", placeHolder: "TEXTAREA"],
                        [id: "select1", type: "select", label: "Auswahl", placeHolder: "AUSWAHL",
                         choices: [[value: ""], [value: "Option 1"], [value: "Option 2"]]],
                        [id: "check1", type: "checkbox", label: "Kontrollkästchen", placeHolder: "CHECKBOX"],
                        [id: "date1", type: "date", label: "Datum", placeHolder: "DATUM"],
                        [id: "number1", type: "number", label: "Ganzzahl", placeHolder: "GANZZAHL"],
                        [id: "amount1", type: "amount", label: "Betrag", placeHolder: "BETRAG"]
                    ]
                ]
            ]
        ]
        return JsonOutput.toJson(defaultDef)
    }

    private void rebuildDynamicUi(String jsonDefinition) {
        if (jsonDefinition == null || jsonDefinition.trim().isEmpty()) {
            return
        }

        try {
            currentUiDefinition = jsonDefinition

            // Remove all dynamic tabs, keep the last two (Einstellungen and Hilfe)
            while (tabbedPane.getTabCount() > 2) {
                tabbedPane.removeTabAt(0)
            }

            def slurper = new JsonSlurper()
            def uiDef = slurper.parseText(jsonDefinition)

            int tabIndex = 0
            uiDef.tabs.each { tabDef ->
                JPanel tabPanel = createTabPanel(tabDef)
                tabbedPane.insertTab(tabDef.tabTitle, null, new JScrollPane(tabPanel), null, tabIndex)
                tabIndex++
            }

            if (tabbedPane.getTabCount() > 1) {
                tabbedPane.setSelectedIndex(0)
            }

            uiBuilt = true
            populateElementsTable()
            updateTabComboBox()

        } catch (Exception e) {
            println("Error parsing UI definition: " + e.getMessage())
            e.printStackTrace()
        }
    }

    private JPanel createTabPanel(def tabDef) {
        JPanel panel = new JPanel()
        panel.setLayout(new GridBagLayout())
        GridBagConstraints con = new GridBagConstraints()
        con.fill = GridBagConstraints.HORIZONTAL
        con.weightx = 1.0
        con.insets = new Insets(2, 5, 2, 5)

        int row = 0

        tabDef.fields.each { fieldDef ->
            FreeformField field = new FreeformField()
            field.setId(fieldDef.id)
            field.setTabTitle(tabDef.tabTitle)
            field.setLabel(fieldDef.label)
            field.setType(fieldDef.type)
            field.setPlaceHolder(fieldDef.placeHolder)

            if (fieldDef.choices != null) {
                fieldDef.choices.each { choice ->
                    field.addChoice(choice.value)
                }
            }

            row = addFieldToPanel(panel, field, con, row)
        }

        // Add filler at the bottom
        con.gridx = 0
        con.gridy = row
        con.weighty = 1.0
        con.fill = GridBagConstraints.BOTH
        panel.add(new JLabel(""), con)

        return panel
    }

    private int addFieldToPanel(JPanel panel, FreeformField field, GridBagConstraints con, int row) {
        String type = field.getType()

        con.weighty = 0.0
        con.fill = GridBagConstraints.HORIZONTAL

        if ("separator".equals(type)) {
            con.gridx = 0
            con.gridy = row
            con.gridwidth = 2
            JSeparator sep = new JSeparator()
            panel.add(sep, con)
            con.gridwidth = 1
            return row + 1

        } else if ("section".equals(type)) {
            con.gridx = 0
            con.gridy = row
            con.gridwidth = 2
            JLabel label = new JLabel("<html><b>" + field.getLabel() + "</b></html>")
            panel.add(label, con)
            con.gridwidth = 1
            return row + 1

        } else if ("spacer".equals(type)) {
            con.gridx = 0
            con.gridy = row
            con.gridwidth = 2
            panel.add(new JLabel(" "), con)
            con.gridwidth = 1
            return row + 1

        } else if ("checkbox".equals(type)) {
            con.gridx = 0
            con.gridy = row
            panel.add(new JLabel(""), con)

            con.gridx = 1
            JCheckBox cb = new JCheckBox(field.getLabel())
            cb.setName(field.getPlaceHolderName())
            cb.putClientProperty("Jlawyerdescription", field.getLabel())
            panel.add(cb, con)
            return row + 1

        } else {
            // Label in first column
            con.gridx = 0
            con.gridy = row
            panel.add(new JLabel(field.getLabel()), con)

            con.gridx = 1

            if ("textbox".equals(type)) {
                JTextField tf = new JTextField("", TEXTFIELD_MAXCOLUMNS)
                tf.setName(field.getPlaceHolderName())
                tf.putClientProperty("Jlawyerdescription", field.getLabel())
                panel.add(tf, con)

            } else if ("textarea".equals(type)) {
                JTextArea ta = new JTextArea()
                ta.setRows(4)
                ta.setLineWrap(true)
                ta.setWrapStyleWord(true)
                ta.setName(field.getPlaceHolderName())
                ta.putClientProperty("Jlawyerdescription", field.getLabel())
                JScrollPane scrollPane = new JScrollPane(ta)
                scrollPane.setPreferredSize(new Dimension(300, 80))
                panel.add(scrollPane, con)

            } else if ("select".equals(type)) {
                JComboBox cb = new JComboBox()
                cb.setEditable(true)
                field.getChoices().each { choice ->
                    cb.addItem(choice)
                }
                cb.setName(field.getPlaceHolderName())
                cb.putClientProperty("Jlawyerdescription", field.getLabel())
                panel.add(cb, con)

            } else if ("date".equals(type)) {
                JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
                JTextField tf = new JTextField("", 15)
                tf.setName(field.getPlaceHolderName())
                tf.setEnabled(false)
                tf.putClientProperty("Jlawyerdescription", field.getLabel())
                datePanel.add(tf)

                JButton dateButton = new JButton()
                dateButton.setIcon(new ImageIcon(getClass().getResource("/icons/schedule.png")))
                dateButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        GuiLib.dateSelector(tf, true)
                    }
                })
                datePanel.add(dateButton)
                panel.add(datePanel, con)

            } else if ("number".equals(type)) {
                JFormattedTextField tf = new JFormattedTextField()
                tf.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(
                    new javax.swing.text.NumberFormatter(new DecimalFormat("0"))))
                tf.setHorizontalAlignment(JTextField.RIGHT)
                tf.setValue(0)
                tf.setColumns(15)
                tf.setName(field.getPlaceHolderName())
                tf.putClientProperty("Jlawyerdescription", field.getLabel())
                panel.add(tf, con)

            } else if ("amount".equals(type)) {
                JFormattedTextField tf = new JFormattedTextField()
                tf.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(
                    new javax.swing.text.NumberFormatter(new DecimalFormat("#0.00"))))
                tf.setHorizontalAlignment(JTextField.RIGHT)
                tf.setValue(0.0f)
                tf.setColumns(15)
                tf.setName(field.getPlaceHolderName())
                tf.putClientProperty("Jlawyerdescription", field.getLabel())
                panel.add(tf, con)

            } else {
                // Default: textbox
                JTextField tf = new JTextField("", TEXTFIELD_MAXCOLUMNS)
                tf.setName(field.getPlaceHolderName())
                tf.putClientProperty("Jlawyerdescription", field.getLabel())
                panel.add(tf, con)
            }

            return row + 1
        }
    }

    private void populateElementsTable() {
        if (tableModel == null || currentUiDefinition == null) {
            return
        }

        tableModel.setRowCount(0)

        try {
            def slurper = new JsonSlurper()
            def uiDef = slurper.parseText(currentUiDefinition)

            uiDef.tabs.each { tabDef ->
                tabDef.fields.each { fieldDef ->
                    tableModel.addRow([
                        tabDef.tabTitle,
                        fieldDef.id,
                        getDisplayNameForType(fieldDef.type),
                        fieldDef.label ?: "",
                        fieldDef.placeHolder ?: ""
                    ] as Object[])
                }
            }
        } catch (Exception e) {
            println("Error populating elements table: " + e.getMessage())
        }
    }

    private void updateTabComboBox() {
        if (cmbTabSelect == null || currentUiDefinition == null) {
            return
        }

        cmbTabSelect.removeAllItems()
        cmbTabSelect.addItem("-- Neuer Tab --")

        try {
            def slurper = new JsonSlurper()
            def uiDef = slurper.parseText(currentUiDefinition)

            uiDef.tabs.each { tabDef ->
                cmbTabSelect.addItem(tabDef.tabTitle)
            }
        } catch (Exception e) {
            println("Error updating tab combo box: " + e.getMessage())
        }
    }

    private void addOrUpdateElement() {
        String tabTitle = null
        if (cmbTabSelect.getSelectedIndex() == 0) {
            tabTitle = txtNewTabName.getText().trim()
            if (tabTitle.isEmpty()) {
                JOptionPane.showMessageDialog(SCRIPTPANEL, "Bitte geben Sie einen Tab-Namen ein.", "Fehler", JOptionPane.ERROR_MESSAGE)
                return
            }
        } else {
            tabTitle = cmbTabSelect.getSelectedItem().toString()
        }

        String elementId = txtElementId.getText().trim()
        if (elementId.isEmpty()) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Bitte geben Sie eine Element-ID ein.", "Fehler", JOptionPane.ERROR_MESSAGE)
            return
        }

        String elementTypeDisplay = cmbElementType.getSelectedItem().toString()
        String elementType = getTechnicalNameForDisplay(elementTypeDisplay)
        String elementLabel = txtElementLabel.getText().trim()
        String elementPlaceholder = txtElementPlaceholder.getText().trim()
        String choicesText = txtElementChoices.getText().trim()

        try {
            def slurper = new JsonSlurper()
            def uiDef = currentUiDefinition != null ? slurper.parseText(currentUiDefinition) : [tabs: []]

            // First, search for existing element with same ID across ALL tabs
            def sourceTab = null
            def existingField = null
            for (tab in uiDef.tabs) {
                def found = tab.fields.find { it.id == elementId }
                if (found != null) {
                    sourceTab = tab
                    existingField = found
                    break
                }
            }

            // Find or create target tab
            def targetTab = uiDef.tabs.find { it.tabTitle == tabTitle }
            if (targetTab == null) {
                targetTab = [tabTitle: tabTitle, fields: []]
                uiDef.tabs.add(targetTab)
            }

            // If element exists in a different tab, remove it from source tab first (move operation)
            if (existingField != null && sourceTab != null && sourceTab.tabTitle != tabTitle) {
                sourceTab.fields.remove(existingField)
                // Remove source tab if it became empty
                if (sourceTab.fields.isEmpty()) {
                    uiDef.tabs.remove(sourceTab)
                }
                existingField = null  // Treat as new element in target tab
            }

            // Check if element exists in target tab and update, or add new
            def fieldInTargetTab = targetTab.fields.find { it.id == elementId }
            if (fieldInTargetTab != null) {
                fieldInTargetTab.type = elementType
                fieldInTargetTab.label = elementLabel
                fieldInTargetTab.placeHolder = elementPlaceholder
                if ("select" == elementType && !choicesText.isEmpty()) {
                    fieldInTargetTab.choices = choicesText.split(",").collect { [value: it.trim()] }
                } else {
                    fieldInTargetTab.remove('choices')
                }
            } else {
                def newField = [
                    id: elementId,
                    type: elementType,
                    label: elementLabel,
                    placeHolder: elementPlaceholder
                ]
                if ("select" == elementType && !choicesText.isEmpty()) {
                    newField.choices = choicesText.split(",").collect { [value: it.trim()] }
                }
                targetTab.fields.add(newField)
            }

            currentUiDefinition = JsonOutput.toJson(uiDef)
            populateElementsTable()
            updateTabComboBox()
            clearElementForm()

        } catch (Exception e) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Fehler beim Hinzufügen: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE)
        }
    }

    private void deleteSelectedElement() {
        int selectedRow = elementsTable.getSelectedRow()
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Bitte wählen Sie ein Element aus.", "Hinweis", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        String tabTitle = tableModel.getValueAt(selectedRow, 0).toString()
        String elementId = tableModel.getValueAt(selectedRow, 1).toString()

        int confirm = JOptionPane.showConfirmDialog(SCRIPTPANEL,
            "Element '" + elementId + "' wirklich löschen?",
            "Löschen bestätigen",
            JOptionPane.YES_NO_OPTION)

        if (confirm != JOptionPane.YES_OPTION) {
            return
        }

        try {
            def slurper = new JsonSlurper()
            def uiDef = slurper.parseText(currentUiDefinition)

            def targetTab = uiDef.tabs.find { it.tabTitle == tabTitle }
            if (targetTab != null) {
                targetTab.fields.removeAll { it.id == elementId }

                // Remove tab if empty
                if (targetTab.fields.isEmpty()) {
                    uiDef.tabs.remove(targetTab)
                }
            }

            currentUiDefinition = JsonOutput.toJson(uiDef)
            populateElementsTable()
            updateTabComboBox()

        } catch (Exception e) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Fehler beim Löschen: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE)
        }
    }

    private void moveElement(int direction) {
        int selectedRow = elementsTable.getSelectedRow()
        if (selectedRow < 0) {
            return
        }

        String tabTitle = tableModel.getValueAt(selectedRow, 0).toString()
        String elementId = tableModel.getValueAt(selectedRow, 1).toString()

        try {
            def slurper = new JsonSlurper()
            def uiDef = slurper.parseText(currentUiDefinition)

            def targetTab = uiDef.tabs.find { it.tabTitle == tabTitle }
            if (targetTab != null) {
                int fieldIndex = targetTab.fields.findIndexOf { it.id == elementId }
                int newIndex = fieldIndex + direction

                if (newIndex >= 0 && newIndex < targetTab.fields.size()) {
                    def field = targetTab.fields.remove(fieldIndex)
                    targetTab.fields.add(newIndex, field)

                    currentUiDefinition = JsonOutput.toJson(uiDef)
                    populateElementsTable()

                    // Reselect the moved row
                    int newSelectedRow = selectedRow + direction
                    if (newSelectedRow >= 0 && newSelectedRow < tableModel.getRowCount()) {
                        elementsTable.setRowSelectionInterval(newSelectedRow, newSelectedRow)
                    }
                }
            }
        } catch (Exception e) {
            println("Error moving element: " + e.getMessage())
        }
    }

    private void refreshUi() {
        if (currentUiDefinition != null && !currentUiDefinition.trim().isEmpty()) {
            rebuildDynamicUi(currentUiDefinition)
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Oberfläche wurde aktualisiert.", "Erfolg", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private void saveConfiguration() {
        if (currentUiDefinition == null || currentUiDefinition.trim().isEmpty()) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Keine Konfiguration vorhanden.", "Fehler", JOptionPane.ERROR_MESSAGE)
            return
        }

        try {
            ServerSettings.getInstance().setSetting("forms.freeform01.uidefinition", currentUiDefinition)
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Konfiguration wurde gespeichert.", "Erfolg", JOptionPane.INFORMATION_MESSAGE)
        } catch (Exception e) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Fehler beim Speichern: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE)
        }
    }

    private void clearElementForm() {
        txtElementId.setText("")
        txtElementLabel.setText("")
        txtElementPlaceholder.setText("")
        txtElementChoices.setText("")
        cmbElementType.setSelectedIndex(0)
    }

    private void loadSelectedElementToForm() {
        int selectedRow = elementsTable.getSelectedRow()
        if (selectedRow < 0) {
            return
        }

        String tabTitle = tableModel.getValueAt(selectedRow, 0).toString()
        String elementId = tableModel.getValueAt(selectedRow, 1).toString()
        String elementTypeDisplay = tableModel.getValueAt(selectedRow, 2).toString()
        String elementLabel = tableModel.getValueAt(selectedRow, 3).toString()
        String elementPlaceholder = tableModel.getValueAt(selectedRow, 4).toString()

        // Select tab in combo box
        for (int i = 0; i < cmbTabSelect.getItemCount(); i++) {
            if (cmbTabSelect.getItemAt(i).equals(tabTitle)) {
                cmbTabSelect.setSelectedIndex(i)
                break
            }
        }

        txtElementId.setText(elementId)
        cmbElementType.setSelectedItem(elementTypeDisplay)
        txtElementLabel.setText(elementLabel)
        txtElementPlaceholder.setText(elementPlaceholder)

        // Load choices if select type
        String elementTypeTechnical = getTechnicalNameForDisplay(elementTypeDisplay)
        if ("select" == elementTypeTechnical) {
            try {
                def slurper = new JsonSlurper()
                def uiDef = slurper.parseText(currentUiDefinition)
                def targetTab = uiDef.tabs.find { it.tabTitle == tabTitle }
                if (targetTab != null) {
                    def field = targetTab.fields.find { it.id == elementId }
                    if (field != null && field.choices != null) {
                        txtElementChoices.setText(field.choices.collect { it.value }.join(", "))
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        } else {
            txtElementChoices.setText("")
        }
    }

    private String getHelpHtml() {
        return '''<html>
<head>
<style>
body { font-family: Arial, sans-serif; padding: 10px; line-height: 1.5; }
h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 5px; }
h2 { color: #34495e; margin-top: 20px; }
h3 { color: #7f8c8d; }
table { border-collapse: collapse; width: 100%; margin: 10px 0; }
th, td { border: 1px solid #bdc3c7; padding: 8px; text-align: left; }
th { background-color: #ecf0f1; }
.hint { background-color: #e8f6f3; border-left: 4px solid #1abc9c; padding: 10px; margin: 10px 0; }
.warning { background-color: #fdf2e9; border-left: 4px solid #e67e22; padding: 10px; margin: 10px 0; }
code { background-color: #f4f4f4; padding: 2px 5px; border-radius: 3px; }
</style>
</head>
<body>

<h1>Anleitung: Frei konfigurierbare Falldaten</h1>

<div class="warning">
<b>Wichtig:</b> Änderungen an der Oberflächen-Konfiguration wirken sich auf <b>ALLE</b> Falldatenblätter
dieses Typs in <b>allen Akten</b> aus! Die Konfiguration ist eine globale Einstellung und nicht aktenspezifisch.
Bereits erfasste Daten bleiben erhalten, aber die Darstellung ändert sich für alle Akten.
</div>

<p>Mit diesem Plugin können Sie eigene Eingabemasken für Falldaten erstellen, ohne programmieren zu müssen.
Die Oberfläche wird über den Tab <b>Einstellungen</b> konfiguriert.</p>

<h2>Schnellstart</h2>
<ol>
<li>Wechseln Sie zum Tab <b>Einstellungen</b></li>
<li>Wählen Sie einen bestehenden Tab oder erstellen Sie einen neuen</li>
<li>Füllen Sie die Felder für das neue Element aus</li>
<li>Klicken Sie auf <b>Hinzufügen/Aktualisieren</b></li>
<li>Klicken Sie auf <b>Oberfläche aktualisieren</b>, um die Änderungen zu sehen</li>
<li>Klicken Sie auf <b>Konfiguration speichern</b>, um die Einstellungen dauerhaft zu speichern</li>
</ol>

<h2>Elementtypen</h2>
<table>
<tr><th>Typ</th><th>Beschreibung</th><th>Verwendung</th></tr>
<tr><td><b>Textfeld (einzeilig)</b></td><td>Einfaches Eingabefeld</td><td>Namen, kurze Texte, Nummern als Text</td></tr>
<tr><td><b>Textfeld (mehrzeilig)</b></td><td>Mehrzeiliges Eingabefeld</td><td>Beschreibungen, Notizen, längere Texte</td></tr>
<tr><td><b>Auswahlfeld</b></td><td>Dropdown-Liste mit Optionen</td><td>Vordefinierte Auswahlen (z.B. Anrede, Status)</td></tr>
<tr><td><b>Kontrollkästchen</b></td><td>Ja/Nein-Auswahl</td><td>Optionen, Bestätigungen</td></tr>
<tr><td><b>Datum</b></td><td>Datumsfeld mit Kalender</td><td>Termine, Fristen, Geburtstage</td></tr>
<tr><td><b>Ganzzahl</b></td><td>Numerisches Feld ohne Dezimalstellen</td><td>Anzahlen, Stückzahlen</td></tr>
<tr><td><b>Betrag (Dezimalzahl)</b></td><td>Numerisches Feld mit 2 Dezimalstellen</td><td>Geldbeträge, Preise</td></tr>
<tr><td><b>Trennlinie</b></td><td>Horizontale Linie</td><td>Visuelle Trennung von Bereichen</td></tr>
<tr><td><b>Abschnittsüberschrift</b></td><td>Fettgedruckte Überschrift</td><td>Gliederung in Abschnitte</td></tr>
<tr><td><b>Leerzeile</b></td><td>Vertikaler Abstand</td><td>Optische Auflockerung</td></tr>
</table>

<h2>Felder im Detail</h2>

<h3>Tab</h3>
<p>Wählen Sie einen bestehenden Tab aus der Liste oder wählen Sie <b>-- Neuer Tab --</b>, um einen neuen Tab anzulegen.
Bei einem neuen Tab geben Sie den gewünschten Namen im Feld <b>Tab-Name</b> ein.</p>

<h3>ID (eindeutiger Bezeichner)</h3>
<p>Die ID identifiziert das Element eindeutig. Sie sollte:</p>
<ul>
<li>Kurz und prägnant sein</li>
<li>Keine Sonderzeichen oder Leerzeichen enthalten</li>
<li>Innerhalb eines Tabs eindeutig sein</li>
</ul>
<p><b>Beispiele:</b> <code>name</code>, <code>geburtsdatum</code>, <code>betrag1</code>, <code>anmerkungen</code></p>

<h3>Label</h3>
<p>Die Beschriftung, die neben dem Eingabefeld angezeigt wird. Bei Kontrollkästchen wird das Label als Text des Kästchens angezeigt.</p>

<h3>Platzhalter</h3>
<p>Der Platzhalter wird in Dokumentvorlagen verwendet. Er wird automatisch in Großbuchstaben umgewandelt und mit einem Unterstrich versehen.</p>
<div class="hint">
<b>Beispiel:</b> Platzhalter <code>VORNAME</code> wird in Vorlagen als <code>{{FREEFORM_VORNAME}}</code> verfügbar.
</div>

<h3>Optionen (bei Auswahlfeld)</h3>
<p>Nur relevant für den Typ <b>Auswahlfeld</b>. Geben Sie die Auswahlmöglichkeiten kommagetrennt ein.</p>
<p><b>Beispiel:</b> <code>Herr, Frau, Divers, Firma</code></p>
<div class="hint">
Fügen Sie am Anfang ein Komma ein, um eine leere Option zu ermöglichen: <code>, Herr, Frau, Divers</code>
</div>

<h2>Tabs verwalten</h2>
<p>Die Oberfläche kann mehrere Tabs enthalten. Ein Tab wird automatisch erstellt, wenn Sie das erste Element mit einem neuen Tab-Namen hinzufügen.
Ein Tab wird automatisch entfernt, wenn Sie das letzte Element darin löschen.</p>

<h2>Elemente bearbeiten</h2>
<ol>
<li>Klicken Sie auf ein Element in der Tabelle</li>
<li>Die Werte werden in das Formular geladen</li>
<li>Ändern Sie die gewünschten Werte</li>
<li>Klicken Sie auf <b>Hinzufügen/Aktualisieren</b></li>
</ol>

<h2>Elemente sortieren</h2>
<p>Wählen Sie ein Element in der Tabelle aus und verwenden Sie die Buttons <b>Nach oben</b> und <b>Nach unten</b>,
um die Reihenfolge zu ändern.</p>

<h2>Buttons</h2>
<table>
<tr><th>Button</th><th>Funktion</th></tr>
<tr><td><b>Hinzufügen/Aktualisieren</b></td><td>Fügt ein neues Element hinzu oder aktualisiert ein bestehendes (gleiche ID)</td></tr>
<tr><td><b>Löschen</b></td><td>Entfernt das ausgewählte Element</td></tr>
<tr><td><b>Nach oben / Nach unten</b></td><td>Ändert die Reihenfolge der Elemente</td></tr>
<tr><td><b>Formular leeren</b></td><td>Leert alle Eingabefelder im Bearbeitungsformular</td></tr>
<tr><td><b>Oberfläche aktualisieren</b></td><td>Baut die dynamischen Tabs neu auf (zeigt Änderungen an)</td></tr>
<tr><td><b>Konfiguration speichern</b></td><td>Speichert die Konfiguration dauerhaft auf dem Server</td></tr>
</table>

<div class="warning">
<b>Wichtig:</b> Änderungen werden erst nach Klick auf <b>Konfiguration speichern</b> dauerhaft gespeichert.
Ohne Speichern gehen Änderungen beim Schließen verloren!
</div>

<h2>Tipps</h2>
<ul>
<li>Strukturieren Sie Ihre Eingabemaske mit <b>Abschnittsüberschriften</b> und <b>Trennlinien</b></li>
<li>Verwenden Sie sprechende Platzhalter-Namen für einfache Zuordnung in Vorlagen</li>
<li>Testen Sie die Oberfläche mit <b>Oberfläche aktualisieren</b>, bevor Sie speichern</li>
<li>Nutzen Sie mehrere Tabs, um umfangreiche Formulare übersichtlich zu gliedern</li>
</ul>

</body>
</html>'''
    }

    public JPanel getUi() {

        SwingBuilder swing = new SwingBuilder()

        swing.edt {
            SCRIPTPANEL = panel(size: [300, 300]) {
                vbox {
                    tabbedPane = tabbedPane(id: 'tabs', tabPlacement: JTabbedPane.LEFT) {

                        // Settings tab - will always be last
                        settingsPanel = panel(name: 'Einstellungen') {
                            borderLayout()

                            panel(constraints: BorderLayout.CENTER) {
                                borderLayout()

                                // Table with elements
                                panel(constraints: BorderLayout.CENTER) {
                                    borderLayout()
                                    label(text: 'Konfigurierte Elemente:', constraints: BorderLayout.NORTH)

                                    scrollPane(constraints: BorderLayout.CENTER) {
                                        tableModel = new DefaultTableModel(
                                            ['Tab', 'ID', 'Typ', 'Label', 'Platzhalter'] as String[],
                                            0
                                        ) {
                                            public boolean isCellEditable(int row, int column) {
                                                return false
                                            }
                                        }
                                        elementsTable = table(model: tableModel, selectionMode: ListSelectionModel.SINGLE_SELECTION)
                                        elementsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                                            public void valueChanged(ListSelectionEvent e) {
                                                if (!e.getValueIsAdjusting()) {
                                                    loadSelectedElementToForm()
                                                }
                                            }
                                        })
                                    }
                                }

                                // Buttons for table operations
                                panel(constraints: BorderLayout.EAST) {
                                    vbox {
                                        button(text: 'Nach oben', actionPerformed: { moveElement(-1) })
                                        button(text: 'Nach unten', actionPerformed: { moveElement(1) })
                                        vstrut(height: 20)
                                        button(text: 'Löschen', actionPerformed: { deleteSelectedElement() })
                                    }
                                }
                            }

                            // Bottom section with form and global buttons
                            panel(constraints: BorderLayout.SOUTH) {
                                borderLayout()

                                // Form for adding/editing elements
                                panel(constraints: BorderLayout.CENTER, border: titledBorder('Element hinzufügen/bearbeiten')) {
                                    tableLayout(cellpadding: 5) {
                                        tr {
                                            td { label(text: 'Tab:') }
                                            td {
                                                panel {
                                                    flowLayout(alignment: FlowLayout.LEFT)
                                                    cmbTabSelect = comboBox(items: ['-- Neuer Tab --'])
                                                    cmbTabSelect.addActionListener(new ActionListener() {
                                                        public void actionPerformed(ActionEvent e) {
                                                            newTabPanel.setVisible(cmbTabSelect.getSelectedIndex() == 0)
                                                        }
                                                    })
                                                }
                                            }
                                        }
                                        tr {
                                            td { label(text: '') }
                                            td {
                                                newTabPanel = panel {
                                                    flowLayout(alignment: FlowLayout.LEFT)
                                                    label(text: 'Tab-Name: ')
                                                    txtNewTabName = textField(columns: 20)
                                                }
                                            }
                                        }
                                        tr {
                                            td { label(text: 'ID (eindeutiger Bezeichner):') }
                                            td { txtElementId = textField(columns: 20) }
                                        }
                                        tr {
                                            td { label(text: 'Typ:') }
                                            td { cmbElementType = comboBox(items: ELEMENT_TYPE_MAP.values() as List) }
                                        }
                                        tr {
                                            td { label(text: 'Label:') }
                                            td { txtElementLabel = textField(columns: 30) }
                                        }
                                        tr {
                                            td { label(text: 'Platzhalter:') }
                                            td { txtElementPlaceholder = textField(columns: 20) }
                                        }
                                        tr {
                                            td { label(text: 'Optionen (bei Auswahl):') }
                                            td {
                                                scrollPane(preferredSize: [300, 60]) {
                                                    txtElementChoices = textArea(lineWrap: true, wrapStyleWord: true)
                                                }
                                            }
                                        }
                                        tr {
                                            td(colspan: 2) {
                                                panel {
                                                    flowLayout(alignment: FlowLayout.LEFT)
                                                    button(text: 'Hinzufügen/Aktualisieren', actionPerformed: { addOrUpdateElement() })
                                                }
                                            }
                                        }
                                    }
                                }

                                // Global buttons outside the border
                                panel(constraints: BorderLayout.SOUTH) {
                                    flowLayout(alignment: FlowLayout.LEFT)
                                    button(text: 'Formular leeren', actionPerformed: { clearElementForm() })
                                    hstrut(width: 20)
                                    button(text: 'Oberfläche aktualisieren', actionPerformed: { refreshUi() })
                                    hstrut(width: 20)
                                    button(text: 'Konfiguration speichern', actionPerformed: { saveConfiguration() })
                                }
                            }
                        }

                        // Help tab - always last
                        panel(name: 'Hilfe') {
                            borderLayout()
                            def helpEditorPane = editorPane(
                                editable: false,
                                contentType: 'text/html',
                                text: getHelpHtml()
                            )
                            def helpScrollPane = scrollPane(constraints: BorderLayout.CENTER, viewportView: helpEditorPane)
                            helpScrollPane.setPreferredSize(new Dimension(600, 400))
                            helpEditorPane.setCaretPosition(0)
                        }
                    }
                }
            }
        }

        // Load and build initial UI
        String uiDef = loadUiDefinition()
        if (uiDef != null && !uiDef.isEmpty()) {
            rebuildDynamicUi(uiDef)
        }

        return SCRIPTPANEL
    }
}
