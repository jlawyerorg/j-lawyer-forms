import groovy.swing.SwingBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.awt.*
import java.awt.event.*
import java.awt.datatransfer.*
import javax.swing.*
import javax.swing.table.*
import javax.swing.event.*
import javax.swing.text.JTextComponent
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.LinkedHashMap
import java.util.List
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback
import com.jdimension.jlawyer.client.settings.ServerSettings

public class freeform01_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    static final String SETTING_KEY = "forms.freeform01.uidefinition"

    JPanel SCRIPTPANEL = null
    FormPluginCallback callback = null

    JTabbedPane tabbedPane = null
    JPanel settingsPanel = null
    JPanel helpPanel = null

    // Settings tab - element list
    JTable elementsTable = null
    DefaultTableModel tableModel = null
    List rowRefs = new ArrayList()

    // Settings tab - detail form
    JComboBox cmbTabSelect = null
    JComboBox cmbElementType = null
    JTextField txtElementId = null
    JTextField txtElementLabel = null
    JTextField txtElementPlaceholder = null
    JLabel lblPlaceholderPreview = null
    JTextArea txtElementChoices = null
    JCheckBox chkEmptyChoice = null
    JTextField txtElementDefault = null
    JSpinner spnColumns = null
    JSpinner spnRows = null
    JTextField txtElementTooltip = null
    JCheckBox chkAdvanced = null
    JPanel advancedPanel = null
    JPanel formPanel = null
    Map formRows = new LinkedHashMap()

    // Settings tab - buttons
    JButton btnApply = null
    JButton btnNewElement = null
    JButton btnDelete = null
    JButton btnUp = null
    JButton btnDown = null
    JButton btnDuplicate = null
    JButton btnTabNew = null
    JButton btnTabRename = null
    JButton btnTabUp = null
    JButton btnTabDown = null
    JButton btnSave = null
    JButton btnDiscard = null
    JButton btnLoadDefault = null
    JLabel lblStatus = null

    // Editor state
    def uiModel = null
    String savedUiDefinition = null
    String loadError = null
    boolean dirty = false
    int pendingChanges = 0
    boolean previewOutdated = false
    boolean uiBuilt = false

    // Re-entrancy guards
    boolean suppressSelectionEvents = false
    boolean updatingCombo = false
    boolean rebuilding = false
    boolean settingIdProgrammatically = false
    boolean idManuallyEdited = false

    // Identity of the element currently loaded into the detail form (null = new element)
    String editingFieldId = null
    String editingTabTitle = null

    static final Color COLOR_DIRTY = new Color(0xB9, 0x6A, 0x00)
    static final Color COLOR_ERROR = new Color(0xC0, 0x39, 0x2B)
    static final Color COLOR_OK = new Color(0x27, 0x7A, 0x3C)

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

    // Which detail form rows are meaningful for which element type
    static final LinkedHashMap<String, List<String>> TYPE_ROWS = [
        'textbox':   ['label', 'placeholder', 'defaultvalue', 'columns'],
        'textarea':  ['label', 'placeholder', 'defaultvalue', 'columns', 'rows'],
        'select':    ['label', 'placeholder', 'choices', 'emptychoice', 'defaultvalue', 'columns'],
        'checkbox':  ['label', 'placeholder', 'defaultvalue'],
        'date':      ['label', 'placeholder', 'defaultvalue'],
        'number':    ['label', 'placeholder', 'defaultvalue', 'columns'],
        'amount':    ['label', 'placeholder', 'defaultvalue', 'columns'],
        'section':   ['label'],
        'separator': [],
        'spacer':    []
    ]

    static final List<String> INPUT_TYPES = ['textbox', 'textarea', 'select', 'checkbox', 'date', 'number', 'amount']
    static final List<String> DECORATION_TYPES = ['separator', 'spacer']

    int TEXTFIELD_MAXCOLUMNS = 50

    public freeform01_ui() {
        super()
    }

    // ------------------------------------------------------------------
    // FormPluginMethods
    // ------------------------------------------------------------------

    public String getAsHtml() {
        return GuiLib.getAsHtml(this.SCRIPTPANEL)
    }

    public ArrayList<String> getPlaceHolders(String prefix) {
        return FormsLib.getPlaceHolders(prefix, this.SCRIPTPANEL)
    }

    public Hashtable getPlaceHolderValues(String prefix) {
        return FormsLib.getPlaceHolderValues(prefix, this.SCRIPTPANEL)
    }

    public Hashtable getPlaceHolderDescriptions(String prefix) {
        return FormsLib.getPlaceHolderDescriptions(prefix, this.SCRIPTPANEL)
    }

    public void setPlaceHolderValues(String prefix, Hashtable placeHolderValues) {
        if (!uiBuilt && tabbedPane != null) {
            if (uiModel == null) {
                loadModelFromJson(loadUiDefinition())
            }
            if (uiModel != null) {
                try {
                    rebuildDynamicUi(true)
                } catch (Exception e) {
                    setStatus("Oberfläche konnte nicht aufgebaut werden: " + e.getMessage(), COLOR_ERROR)
                }
            }
        }
        FormsLib.setPlaceHolderValues(prefix, placeHolderValues, this.SCRIPTPANEL)
    }

    public void setCallback(FormPluginCallback callback) {
        this.callback = callback
    }

    // ------------------------------------------------------------------
    // Model loading / serialization
    // ------------------------------------------------------------------

    private String loadUiDefinition() {
        String setting = ServerSettings.getInstance().getSetting(SETTING_KEY, "")
        if (setting == null || setting.trim().isEmpty()) {
            return getDefaultJsonDefinition()
        }
        return setting
    }

    private String getDefaultJsonDefinition() {
        def defaultDef = [
            schemaVersion: 2,
            tabs: [
                [
                    tabTitle: "Beispieldaten",
                    fields: [
                        [id: "text1", type: "textbox", label: "Textfeld", placeHolder: "TEXTFELD", columns: 30],
                        [id: "area1", type: "textarea", label: "Mehrzeiliges Textfeld", placeHolder: "TEXTAREA", columns: 40, rows: 4],
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

    /** Converts the JsonSlurper result into plain, freely mutable maps and lists. */
    private Object deepPlain(Object o) {
        if (o instanceof Map) {
            def m = new LinkedHashMap()
            o.each { k, v -> m.put(k.toString(), deepPlain(v)) }
            return m
        }
        if (o instanceof List) {
            def l = new ArrayList()
            o.each { l.add(deepPlain(it)) }
            return l
        }
        return o
    }

    private boolean loadModelFromJson(String json) {
        this.loadError = null
        try {
            if (json == null || json.trim().isEmpty()) {
                throw new IllegalArgumentException("Die Konfiguration ist leer.")
            }
            def parsed = deepPlain(new JsonSlurper().parseText(json))
            if (!(parsed instanceof Map) || parsed.get("tabs") == null) {
                throw new IllegalArgumentException("Die Konfiguration enthält keinen Abschnitt 'tabs'.")
            }
            parsed.tabs.each { t ->
                if (t.get("fields") == null) {
                    t.put("fields", new ArrayList())
                }
                if (t.get("tabTitle") == null) {
                    t.put("tabTitle", "Ohne Titel")
                }
            }
            parsed.put("schemaVersion", 2)
            this.uiModel = parsed
            return true
        } catch (Exception e) {
            this.uiModel = null
            this.loadError = firstLine(e.getMessage())
            return false
        }
    }

    /** JSON parser messages span many lines - the status bar only has room for the first one. */
    private String firstLine(String message) {
        if (message == null) {
            return "unbekannter Fehler"
        }
        String line = message.trim().split("\n")[0].trim()
        if (line.length() > 160) {
            line = line.substring(0, 160) + "…"
        }
        return line
    }

    private String getCurrentUiDefinition() {
        if (uiModel == null) {
            return null
        }
        return JsonOutput.toJson(uiModel)
    }

    // ------------------------------------------------------------------
    // Dynamic (data) tabs
    // ------------------------------------------------------------------

    private int getSettingsTabIndex() {
        return tabbedPane.indexOfComponent(settingsPanel)
    }

    private void removeDynamicTabs() {
        int idx = getSettingsTabIndex()
        for (int i = idx - 1; i >= 0; i--) {
            tabbedPane.removeTabAt(i)
        }
    }

    private int indexOfTabTitle(String title) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (title != null && title.equals(tabbedPane.getTitleAt(i))) {
                return i
            }
        }
        return -1
    }

    /** Rebuilds the data tabs from the model. Throws on malformed definitions - callers must report. */
    private void rebuildDynamicUi(boolean selectFirstTab) {
        if (uiModel == null) {
            return
        }
        boolean previous = rebuilding
        rebuilding = true
        try {
            removeDynamicTabs()
            int tabIndex = 0
            uiModel.tabs.each { tabDef ->
                JPanel tabPanel = createTabPanel(tabDef)
                JScrollPane sp = new JScrollPane(tabPanel)
                sp.getVerticalScrollBar().setUnitIncrement(16)
                tabbedPane.insertTab(tabDef.tabTitle, null, sp, null, tabIndex)
                tabIndex++
            }
            uiBuilt = true
            if (selectFirstTab && tabIndex > 0) {
                tabbedPane.setSelectedIndex(0)
            }
        } finally {
            rebuilding = previous
        }
    }

    /**
     * Rebuilds the data tabs without losing values the user already entered into the case:
     * the values are harvested first and written back afterwards.
     */
    private void rebuildPreservingValues(boolean selectFirstTab) {
        Hashtable values = null
        if (uiBuilt) {
            try {
                values = FormsLib.getPlaceHolderValues("", SCRIPTPANEL)
            } catch (Exception e) {
                values = null
            }
        }
        rebuildDynamicUi(selectFirstTab)
        if (values != null && !values.isEmpty()) {
            try {
                FormsLib.setPlaceHolderValues("", values, SCRIPTPANEL)
            } catch (Exception e) {
                // values that no longer have a matching component are simply dropped
            }
        }
        previewOutdated = false
    }

    private JPanel createTabPanel(def tabDef) {
        JPanel panel = new JPanel()
        panel.setLayout(new GridBagLayout())
        GridBagConstraints con = new GridBagConstraints()
        con.insets = new Insets(2, 5, 2, 5)

        int row = 0
        tabDef.fields.each { fieldDef ->
            FreeformField field = toField(tabDef, fieldDef)
            row = addFieldToPanel(panel, field, fieldDef, con, row)
        }

        // filler at the bottom so the fields stay top-aligned
        con.gridx = 0
        con.gridy = row
        con.gridwidth = 2
        con.weightx = 1.0
        con.weighty = 1.0
        con.fill = GridBagConstraints.BOTH
        panel.add(new JLabel(""), con)
        con.gridwidth = 1

        return panel
    }

    /**
     * FreeformField is a separate plugin file and is deliberately used with its original API only.
     * The plugin loader parses every file on its own, so a client that still holds an older
     * FreeformField class would fail on any newly added member. The additional per-field options
     * are therefore read straight from the JSON definition in addFieldToPanel().
     */
    private FreeformField toField(def tabDef, def fieldDef) {
        FreeformField field = new FreeformField()
        field.setId(fieldDef.id)
        field.setTabTitle(tabDef?.tabTitle)
        field.setLabel(fieldDef.label)
        field.setType(fieldDef.type)
        field.setPlaceHolder(fieldDef.placeHolder)
        if (fieldDef.choices != null) {
            fieldDef.choices.each { choice -> field.addChoice(choice.value != null ? choice.value : "") }
        }
        return field
    }

    private Integer toPositiveInt(def value) {
        if (value == null) {
            return null
        }
        try {
            int i = Integer.parseInt(value.toString())
            return i > 0 ? Integer.valueOf(i) : null
        } catch (Exception e) {
            return null
        }
    }

    private void applyCommonProperties(JComponent comp, FreeformField field, String tooltip) {
        comp.setName(field.getPlaceHolderName())
        comp.putClientProperty("Jlawyerdescription", field.getLabel() != null ? field.getLabel() : "")
        if (tooltip != null && !tooltip.trim().isEmpty()) {
            comp.setToolTipText(tooltip)
        }
    }

    private int addFieldToPanel(JPanel panel, FreeformField field, def fieldDef, GridBagConstraints con, int row) {
        String type = field.getType()
        String defaultValue = fieldDef.defaultValue
        String tooltip = fieldDef.tooltip
        Integer configuredColumns = toPositiveInt(fieldDef.columns)
        Integer configuredRows = toPositiveInt(fieldDef.rows)

        con.weighty = 0.0
        con.gridy = row

        if ("separator".equals(type)) {
            con.gridx = 0
            con.gridwidth = 2
            con.weightx = 1.0
            con.fill = GridBagConstraints.HORIZONTAL
            con.anchor = GridBagConstraints.CENTER
            panel.add(new JSeparator(), con)
            con.gridwidth = 1
            return row + 1

        } else if ("section".equals(type)) {
            con.gridx = 0
            con.gridwidth = 2
            con.weightx = 1.0
            con.fill = GridBagConstraints.HORIZONTAL
            con.anchor = GridBagConstraints.LINE_START
            con.insets = new Insets(10, 5, 2, 5)
            JLabel label = new JLabel("<html><b>" + escapeForLabel(field.getLabel()) + "</b></html>")
            if (tooltip != null && !tooltip.trim().isEmpty()) {
                label.setToolTipText(tooltip)
            }
            panel.add(label, con)
            con.insets = new Insets(2, 5, 2, 5)
            con.gridwidth = 1
            return row + 1

        } else if ("spacer".equals(type)) {
            con.gridx = 0
            con.gridwidth = 2
            con.weightx = 1.0
            con.fill = GridBagConstraints.HORIZONTAL
            panel.add(new JLabel(" "), con)
            con.gridwidth = 1
            return row + 1

        } else if ("checkbox".equals(type)) {
            con.gridx = 0
            con.weightx = 0.0
            con.fill = GridBagConstraints.NONE
            con.anchor = GridBagConstraints.LINE_START
            panel.add(new JLabel(""), con)

            con.gridx = 1
            con.weightx = 1.0
            con.fill = GridBagConstraints.HORIZONTAL
            JCheckBox cb = new JCheckBox(field.getLabel())
            cb.setSelected(isTrue(defaultValue))
            applyCommonProperties(cb, field, tooltip)
            panel.add(cb, con)
            return row + 1
        }

        // label column stays as narrow as the text and is left aligned
        con.gridx = 0
        con.weightx = 0.0
        con.fill = GridBagConstraints.NONE
        con.anchor = GridBagConstraints.LINE_START
        JLabel lbl = new JLabel(field.getLabel())
        if (tooltip != null && !tooltip.trim().isEmpty()) {
            lbl.setToolTipText(tooltip)
        }
        panel.add(lbl, con)

        // control column takes the remaining width
        con.gridx = 1
        con.weightx = 1.0
        con.fill = GridBagConstraints.HORIZONTAL

        if ("textarea".equals(type)) {
            JTextArea ta = new JTextArea()
            ta.setRows(configuredRows != null ? configuredRows.intValue() : 4)
            ta.setColumns(configuredColumns != null ? configuredColumns.intValue() : 40)
            ta.setLineWrap(true)
            ta.setWrapStyleWord(true)
            if (defaultValue != null) {
                ta.setText(defaultValue)
            }
            applyCommonProperties(ta, field, tooltip)
            panel.add(new JScrollPane(ta), con)

        } else if ("select".equals(type)) {
            JComboBox cb = new JComboBox()
            cb.setEditable(true)
            List choices = field.getChoices()
            if (choices == null || choices.isEmpty()) {
                // an editable combo box without any item returns null from getSelectedItem(),
                // which would break FormsLib when the placeholder values are collected
                cb.addItem("")
            } else {
                choices.each { choice -> cb.addItem(choice) }
            }
            if (defaultValue != null && !defaultValue.isEmpty()) {
                cb.setSelectedItem(defaultValue)
            } else {
                cb.setSelectedIndex(0)
            }
            applyCommonProperties(cb, field, tooltip)
            panel.add(cb, con)

        } else if ("date".equals(type)) {
            JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
            JTextField tf = new JTextField("", 12)
            // read-only rather than disabled: the text stays selectable, copyable and focusable
            tf.setEditable(false)
            if (defaultValue != null) {
                tf.setText(defaultValue)
            }
            applyCommonProperties(tf, field, tooltip)
            datePanel.add(tf)

            JButton dateButton = new JButton()
            java.net.URL iconUrl = getClass().getResource("/icons/schedule.png")
            if (iconUrl != null) {
                dateButton.setIcon(new ImageIcon(iconUrl))
            } else {
                dateButton.setText("...")
            }
            dateButton.setToolTipText("Datum auswählen")
            dateButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    GuiLib.dateSelector(tf, true)
                }
            })
            datePanel.add(dateButton)

            JButton clearButton = new JButton("×")
            clearButton.setToolTipText("Datum entfernen")
            clearButton.setMargin(new Insets(1, 4, 1, 4))
            clearButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    tf.setText("")
                }
            })
            datePanel.add(clearButton)
            panel.add(datePanel, con)

        } else if ("number".equals(type) || "amount".equals(type)) {
            boolean isAmount = "amount".equals(type)
            JFormattedTextField tf = new JFormattedTextField()
            tf.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(
                new javax.swing.text.NumberFormatter(new DecimalFormat(isAmount ? "#0.00" : "0"))))
            tf.setHorizontalAlignment(JTextField.RIGHT)
            tf.setColumns(configuredColumns != null ? configuredColumns.intValue() : 15)
            // no configured default means the field stays empty, so untouched fields
            // do not push a "0" into the document templates
            if (defaultValue != null && !defaultValue.trim().isEmpty()) {
                try {
                    tf.setValue(isAmount ? Double.valueOf(defaultValue.replace(",", ".")) : Integer.valueOf(defaultValue.trim()))
                } catch (Exception e) {
                    tf.setValue(null)
                }
            } else {
                tf.setValue(null)
            }
            applyCommonProperties(tf, field, tooltip)
            panel.add(tf, con)

        } else {
            // textbox and anything unknown
            JTextField tf = new JTextField("", configuredColumns != null ? configuredColumns.intValue() : TEXTFIELD_MAXCOLUMNS)
            if (defaultValue != null) {
                tf.setText(defaultValue)
            }
            applyCommonProperties(tf, field, tooltip)
            panel.add(tf, con)
        }

        return row + 1
    }

    private boolean isTrue(String value) {
        if (value == null) {
            return false
        }
        String v = value.trim().toLowerCase()
        return "1".equals(v) || "ja".equals(v) || "x".equals(v) || "true".equals(v)
    }

    private String escapeForLabel(String s) {
        if (s == null) {
            return ""
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    // ------------------------------------------------------------------
    // Type helpers
    // ------------------------------------------------------------------

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

    private String getSelectedType() {
        Object sel = cmbElementType.getSelectedItem()
        return sel == null ? "textbox" : getTechnicalNameForDisplay(sel.toString())
    }

    // ------------------------------------------------------------------
    // Model lookup helpers
    // ------------------------------------------------------------------

    private int indexOfIdentity(List list, Object o) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).is(o)) {
                return i
            }
        }
        return -1
    }

    private void removeByIdentity(List list, Object o) {
        int i = indexOfIdentity(list, o)
        if (i >= 0) {
            list.remove(i)
        }
    }

    private def findTabByTitle(String title) {
        if (uiModel == null || title == null) {
            return null
        }
        return uiModel.tabs.find { title.equals(it.tabTitle) }
    }

    private def findFieldById(String id) {
        if (uiModel == null || id == null) {
            return null
        }
        for (tab in uiModel.tabs) {
            def found = tab.fields.find { id.equals(it.id) }
            if (found != null) {
                return [tab: tab, field: found]
            }
        }
        return null
    }

    private Set collectAllIds() {
        Set ids = new LinkedHashSet()
        if (uiModel != null) {
            uiModel.tabs.each { tab -> tab.fields.each { f -> if (f.id != null) ids.add(f.id.toString()) } }
        }
        return ids
    }

    private String makeIdUnique(String base, String ignoreId) {
        Set used = collectAllIds()
        if (ignoreId != null) {
            used.remove(ignoreId)
        }
        String candidate = (base == null || base.trim().isEmpty()) ? "feld" : base.trim()
        if (!used.contains(candidate)) {
            return candidate
        }
        int i = 2
        while (used.contains(candidate + i)) {
            i++
        }
        return candidate + i
    }

    private String deriveId(String label, String type) {
        String base = (label == null ? "" : label).toLowerCase()
        base = base.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        base = base.replaceAll("[^a-z0-9]", "")
        if (base.length() > 24) {
            base = base.substring(0, 24)
        }
        if (base.isEmpty()) {
            base = (type == null ? "feld" : type)
        }
        return makeIdUnique(base, editingFieldId)
    }

    private String nullIfEmpty(String s) {
        if (s == null) {
            return null
        }
        String t = s.trim()
        return t.isEmpty() ? null : t
    }

    private void putOrRemove(Map m, String key, Object value) {
        if (value == null) {
            m.remove(key)
        } else {
            m.put(key, value)
        }
    }

    // ------------------------------------------------------------------
    // Detail form
    // ------------------------------------------------------------------

    private DocumentListener onTextChange(Closure c) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { c.call() }
            public void removeUpdate(DocumentEvent e) { c.call() }
            public void changedUpdate(DocumentEvent e) { c.call() }
        }
    }

    private void addFormRow(JPanel panel, GridBagConstraints con, int row, String key, String labelText, Component control, int anchor) {
        JLabel lbl = new JLabel(labelText)
        con.gridx = 0
        con.gridy = row
        con.gridwidth = 1
        con.weightx = 0.0
        con.fill = GridBagConstraints.NONE
        con.anchor = anchor
        panel.add(lbl, con)

        con.gridx = 1
        con.weightx = 1.0
        con.fill = GridBagConstraints.HORIZONTAL
        con.anchor = GridBagConstraints.LINE_START
        panel.add(control, con)

        formRows.put(key, [lbl, control])
    }

    private void addFormWideRow(JPanel panel, GridBagConstraints con, int row, String key, Component control) {
        con.gridx = 0
        con.gridy = row
        con.gridwidth = 2
        con.weightx = 1.0
        con.fill = GridBagConstraints.HORIZONTAL
        con.anchor = GridBagConstraints.LINE_START
        panel.add(control, con)
        con.gridwidth = 1
        formRows.put(key, [control])
    }

    private void setRowVisible(String key, boolean visible) {
        def row = formRows.get(key)
        if (row == null) {
            return
        }
        row.each { c -> c.setVisible(visible) }
    }

    private void setRowLabel(String key, String text) {
        def row = formRows.get(key)
        if (row != null && row.size() > 1 && row.get(0) instanceof JLabel) {
            ((JLabel) row.get(0)).setText(text)
        }
    }

    /** Shows only the rows that are meaningful for the selected element type. */
    private void updateFormForType() {
        String type = getSelectedType()
        List visible = TYPE_ROWS.get(type)
        if (visible == null) {
            visible = []
        }

        ['label', 'placeholder', 'choices', 'emptychoice', 'defaultvalue', 'columns', 'rows'].each { key ->
            setRowVisible(key, visible.contains(key))
        }

        if ("checkbox".equals(type)) {
            setRowLabel('defaultvalue', 'Standard (ja/nein):')
        } else if ("date".equals(type)) {
            setRowLabel('defaultvalue', 'Standard (TT.MM.JJJJ):')
        } else {
            setRowLabel('defaultvalue', 'Standardwert:')
        }
        setRowLabel('label', "section".equals(type) ? 'Überschrift:' : 'Beschriftung:')

        boolean advanced = chkAdvanced.isSelected()
        advancedPanel.setVisible(advanced)
        setRowVisible('tooltip', INPUT_TYPES.contains(type))

        if (formPanel != null) {
            formPanel.revalidate()
            formPanel.repaint()
        }
    }

    /**
     * Uses a throwaway FreeformField instead of a static helper: the plugin loader parses every
     * file on its own, so a static call across two plugin files can bind to a stale class object,
     * while an instance method is always dispatched on the object that was actually created.
     */
    private String placeHolderNameFor(String placeHolder, String id) {
        FreeformField probe = new FreeformField()
        probe.setPlaceHolder(placeHolder)
        probe.setId(id)
        return probe.getPlaceHolderName()
    }

    /** JTextField.getText() returns null when the document is being modified concurrently. */
    private String safeText(JTextComponent component) {
        if (component == null) {
            return ""
        }
        String text = component.getText()
        return text == null ? "" : text.trim()
    }

    /** Runs deferred on the EDT, so it must tolerate a form that is being rebuilt or cleared. */
    private void updatePlaceholderPreview() {
        if (lblPlaceholderPreview == null) {
            return
        }
        String type = getSelectedType()
        if (!INPUT_TYPES.contains(type)) {
            lblPlaceholderPreview.setText(" ")
            return
        }
        String name = placeHolderNameFor(safeText(txtElementPlaceholder), safeText(txtElementId))
        lblPlaceholderPreview.setText("in Vorlagen:  {{FREEFORM" + name + "}}")
    }

    /**
     * A document listener must not modify another document while it is being notified,
     * so the derived id and the preview are always written back on the next EDT turn.
     */
    private void scheduleIdUpdate(String derivedId) {
        def self = this
        String derived = derivedId
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                self.setIdFieldText(derived)
                self.updatePlaceholderPreview()
            }
        })
    }

    private void schedulePreviewUpdate() {
        def self = this
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                self.updatePlaceholderPreview()
            }
        })
    }

    private void setIdFieldText(String text) {
        settingIdProgrammatically = true
        try {
            txtElementId.setText(text)
        } finally {
            settingIdProgrammatically = false
        }
    }

    private List parseChoicesFromForm() {
        List result = new ArrayList()
        if (chkEmptyChoice.isSelected()) {
            result.add([value: ""])
        }
        String text = txtElementChoices == null ? null : txtElementChoices.getText()
        if (text != null) {
            text.split("\n").each { line ->
                String v = line.trim()
                if (!v.isEmpty()) {
                    result.add([value: v])
                }
            }
        }
        return result
    }

    // ------------------------------------------------------------------
    // Elements table
    // ------------------------------------------------------------------

    private void populateElementsTable() {
        if (tableModel == null) {
            return
        }
        suppressSelectionEvents = true
        try {
            tableModel.setRowCount(0)
            rowRefs.clear()
            if (uiModel == null) {
                return
            }
            uiModel.tabs.each { tabDef ->
                tabDef.fields.each { fieldDef ->
                    String choicesPreview = ""
                    if (fieldDef.choices != null) {
                        choicesPreview = fieldDef.choices.collect { it.value != null ? it.value : "" }.join(" | ")
                        if (choicesPreview.length() > 45) {
                            choicesPreview = choicesPreview.substring(0, 45) + "…"
                        }
                    }
                    tableModel.addRow([
                        tabDef.tabTitle,
                        fieldDef.id,
                        getDisplayNameForType(fieldDef.type),
                        fieldDef.label ?: "",
                        fieldDef.placeHolder ?: "",
                        fieldDef.defaultValue ?: "",
                        choicesPreview
                    ] as Object[])
                    rowRefs.add([tab: tabDef, field: fieldDef])
                }
            }
        } finally {
            suppressSelectionEvents = false
        }
    }

    private def getSingleSelection() {
        if (elementsTable == null || elementsTable.getSelectedRowCount() != 1) {
            return null
        }
        int r = elementsTable.getSelectedRow()
        if (r < 0 || r >= rowRefs.size()) {
            return null
        }
        return rowRefs.get(r)
    }

    private void selectRowFor(String tabTitle, String fieldId) {
        for (int i = 0; i < rowRefs.size(); i++) {
            def ref = rowRefs.get(i)
            if (tabTitle.equals(ref.tab.tabTitle) && fieldId.equals(ref.field.id)) {
                elementsTable.setRowSelectionInterval(i, i)
                elementsTable.scrollRectToVisible(elementsTable.getCellRect(i, 0, true))
                return
            }
        }
    }

    private void updateTabComboBox() {
        if (cmbTabSelect == null) {
            return
        }
        Object previous = cmbTabSelect.getSelectedItem()
        updatingCombo = true
        try {
            cmbTabSelect.removeAllItems()
            if (uiModel != null) {
                uiModel.tabs.each { t -> cmbTabSelect.addItem(t.tabTitle) }
            }
            if (previous != null) {
                for (int i = 0; i < cmbTabSelect.getItemCount(); i++) {
                    if (previous.equals(cmbTabSelect.getItemAt(i))) {
                        cmbTabSelect.setSelectedIndex(i)
                        break
                    }
                }
            }
        } finally {
            updatingCombo = false
        }
    }

    private void selectTabInCombo(String tabTitle) {
        if (tabTitle == null) {
            return
        }
        updatingCombo = true
        try {
            for (int i = 0; i < cmbTabSelect.getItemCount(); i++) {
                if (tabTitle.equals(cmbTabSelect.getItemAt(i))) {
                    cmbTabSelect.setSelectedIndex(i)
                    break
                }
            }
        } finally {
            updatingCombo = false
        }
    }

    private void loadSelectedElementToForm() {
        def ref = getSingleSelection()
        if (ref == null) {
            return
        }
        def f = ref.field

        editingFieldId = f.id
        editingTabTitle = ref.tab.tabTitle
        idManuallyEdited = true

        selectTabInCombo(ref.tab.tabTitle)

        if (ELEMENT_TYPE_MAP.containsKey(f.type)) {
            cmbElementType.setSelectedItem(getDisplayNameForType(f.type))
        } else {
            cmbElementType.setSelectedIndex(0)
            setStatus("Unbekannter Elementtyp '" + f.type + "'. Beim Übernehmen wird er durch die aktuelle Auswahl ersetzt.", COLOR_ERROR)
        }

        setIdFieldText(f.id != null ? f.id : "")
        txtElementLabel.setText(f.label != null ? f.label : "")
        txtElementPlaceholder.setText(f.placeHolder != null ? f.placeHolder : "")
        txtElementDefault.setText(f.defaultValue != null ? f.defaultValue : "")
        txtElementTooltip.setText(f.tooltip != null ? f.tooltip : "")

        Integer cols = toPositiveInt(f.columns)
        Integer rws = toPositiveInt(f.rows)
        spnColumns.setValue(cols != null ? cols : Integer.valueOf(0))
        spnRows.setValue(rws != null ? rws : Integer.valueOf(0))

        List vals = new ArrayList()
        if (f.choices != null) {
            f.choices.each { vals.add(it.value != null ? it.value.toString() : "") }
        }
        boolean leadingEmpty = !vals.isEmpty() && vals.get(0).toString().isEmpty()
        chkEmptyChoice.setSelected(leadingEmpty)
        if (leadingEmpty) {
            vals.remove(0)
        }
        txtElementChoices.setText(vals.join("\n"))

        btnApply.setText("✓ Änderung übernehmen")
        updateFormForType()
        updatePlaceholderPreview()
    }

    private void prepareNewElement(String tabTitle, String type) {
        suppressSelectionEvents = true
        try {
            elementsTable.clearSelection()
        } finally {
            suppressSelectionEvents = false
        }
        editingFieldId = null
        editingTabTitle = null
        idManuallyEdited = false

        setIdFieldText("")
        txtElementLabel.setText("")
        txtElementPlaceholder.setText("")
        txtElementDefault.setText("")
        txtElementTooltip.setText("")
        txtElementChoices.setText("")
        chkEmptyChoice.setSelected(false)
        spnColumns.setValue(Integer.valueOf(0))
        spnRows.setValue(Integer.valueOf(0))

        if (tabTitle != null) {
            selectTabInCombo(tabTitle)
        }
        if (type != null) {
            cmbElementType.setSelectedItem(getDisplayNameForType(type))
        }

        btnApply.setText("✓ Element hinzufügen")
        updateFormForType()
        updatePlaceholderPreview()
        updateButtonStates()
        txtElementLabel.requestFocusInWindow()
    }

    // ------------------------------------------------------------------
    // Editor actions
    // ------------------------------------------------------------------

    private void applyElement() {
        if (uiModel == null) {
            setStatus("Es ist keine gültige Konfiguration geladen.", COLOR_ERROR)
            return
        }
        if (cmbTabSelect.getItemCount() == 0) {
            setStatus("Bitte legen Sie zuerst über 'Neuer Tab' einen Tab an.", COLOR_ERROR)
            return
        }

        String tabTitle = cmbTabSelect.getSelectedItem().toString()
        String type = getSelectedType()
        List rows = TYPE_ROWS.get(type)
        if (rows == null) {
            rows = []
        }
        boolean decoration = DECORATION_TYPES.contains(type)

        String label = safeText(txtElementLabel)
        if (rows.contains('label') && label.isEmpty()) {
            setStatus("Bitte geben Sie eine Beschriftung ein.", COLOR_ERROR)
            txtElementLabel.requestFocusInWindow()
            return
        }

        String id = safeText(txtElementId).replaceAll("\\s+", "")
        if (id.isEmpty()) {
            id = decoration ? makeIdUnique(type, editingFieldId) : deriveId(label, type)
        }
        if (id.isEmpty()) {
            setStatus("Es konnte keine ID ermittelt werden. Bitte im Bereich 'Erweitert' eine ID vergeben.", COLOR_ERROR)
            return
        }

        def clash = findFieldById(id)
        if (clash != null && !id.equals(editingFieldId)) {
            setStatus("Die ID '" + id + "' ist im Tab '" + clash.tab.tabTitle + "' bereits vergeben.", COLOR_ERROR)
            return
        }

        def targetTab = findTabByTitle(tabTitle)
        if (targetTab == null) {
            setStatus("Der Tab '" + tabTitle + "' existiert nicht mehr.", COLOR_ERROR)
            updateTabComboBox()
            return
        }

        boolean wasNew = (editingFieldId == null)
        def fieldMap = null
        def emptiedTab = null

        if (wasNew) {
            fieldMap = new LinkedHashMap()
            targetTab.fields.add(fieldMap)
        } else {
            def orig = findFieldById(editingFieldId)
            if (orig == null) {
                // the element vanished in the meantime - treat the input as a new element
                wasNew = true
                fieldMap = new LinkedHashMap()
                targetTab.fields.add(fieldMap)
            } else {
                fieldMap = orig.field
                if (!orig.tab.tabTitle.equals(tabTitle)) {
                    int answer = GuiLib.askYesNo("Element verschieben?",
                        "Das Element '" + editingFieldId + "' liegt im Tab '" + orig.tab.tabTitle + "'.\n" +
                        "Soll es nach '" + tabTitle + "' verschoben werden?")
                    if (answer != JOptionPane.YES_OPTION) {
                        selectTabInCombo(orig.tab.tabTitle)
                        return
                    }
                    removeByIdentity(orig.tab.fields, fieldMap)
                    targetTab.fields.add(fieldMap)
                    emptiedTab = orig.tab
                }
            }
        }

        fieldMap.put("id", id)
        fieldMap.put("type", type)
        putOrRemove(fieldMap, "label", rows.contains('label') ? label : null)
        putOrRemove(fieldMap, "placeHolder", rows.contains('placeholder') ? nullIfEmpty(safeText(txtElementPlaceholder)) : null)
        putOrRemove(fieldMap, "defaultValue", rows.contains('defaultvalue') ? nullIfEmpty(safeText(txtElementDefault)) : null)
        putOrRemove(fieldMap, "tooltip", INPUT_TYPES.contains(type) ? nullIfEmpty(safeText(txtElementTooltip)) : null)
        putOrRemove(fieldMap, "columns", rows.contains('columns') ? toPositiveInt(spnColumns.getValue()) : null)
        putOrRemove(fieldMap, "rows", rows.contains('rows') ? toPositiveInt(spnRows.getValue()) : null)
        if (rows.contains('choices')) {
            fieldMap.put("choices", parseChoicesFromForm())
        } else {
            fieldMap.remove("choices")
        }

        markDirty(wasNew ? ("Element '" + id + "' hinzugefügt") : ("Element '" + id + "' geändert"))

        if (emptiedTab != null) {
            maybeRemoveEmptyTab(emptiedTab)
        }

        populateElementsTable()
        updateTabComboBox()

        if (wasNew) {
            // stay in the same tab and keep the type - this is the rapid entry flow
            prepareNewElement(tabTitle, type)
        } else {
            editingFieldId = id
            editingTabTitle = tabTitle
            selectRowFor(tabTitle, id)
            updateButtonStates()
        }
    }

    private void deleteSelectedElements() {
        int[] selected = elementsTable.getSelectedRows()
        if (selected.length == 0) {
            return
        }
        List refs = new ArrayList()
        selected.each { r -> if (r >= 0 && r < rowRefs.size()) refs.add(rowRefs.get(r)) }
        if (refs.isEmpty()) {
            return
        }

        String question = refs.size() == 1 ?
            ("Element '" + refs.get(0).field.id + "' wirklich löschen?") :
            (refs.size() + " Elemente wirklich löschen?")
        if (GuiLib.askYesNo("Löschen bestätigen", question) != JOptionPane.YES_OPTION) {
            return
        }

        List touchedTabs = new ArrayList()
        refs.each { ref ->
            removeByIdentity(ref.tab.fields, ref.field)
            if (indexOfIdentity(touchedTabs, ref.tab) < 0) {
                touchedTabs.add(ref.tab)
            }
        }

        markDirty(refs.size() == 1 ? "Element gelöscht" : (refs.size() + " Elemente gelöscht"))
        touchedTabs.each { t -> maybeRemoveEmptyTab(t) }

        populateElementsTable()
        updateTabComboBox()
        prepareNewElement(null, null)
    }

    private void maybeRemoveEmptyTab(def tab) {
        if (tab == null || !tab.fields.isEmpty()) {
            return
        }
        int answer = GuiLib.askYesNo("Leerer Tab",
            "Der Tab '" + tab.tabTitle + "' enthält jetzt keine Elemente mehr.\nSoll der Tab entfernt werden?")
        if (answer == JOptionPane.YES_OPTION) {
            removeByIdentity(uiModel.tabs, tab)
        }
    }

    private void duplicateSelectedElement() {
        def ref = getSingleSelection()
        if (ref == null) {
            return
        }
        def copy = deepPlain(ref.field)
        String newId = makeIdUnique(ref.field.id != null ? ref.field.id.toString() : "feld", null)
        copy.put("id", newId)
        if (copy.get("placeHolder") != null && !copy.get("placeHolder").toString().trim().isEmpty()) {
            // give the copy its own placeholder so the two fields do not overwrite each other
            copy.put("placeHolder", newId.toUpperCase())
        }
        int idx = indexOfIdentity(ref.tab.fields, ref.field)
        ref.tab.fields.add(idx + 1, copy)

        markDirty("Element '" + newId + "' dupliziert")
        populateElementsTable()
        updateTabComboBox()
        selectRowFor(ref.tab.tabTitle, newId)
        updateButtonStates()
    }

    private void moveElement(int direction) {
        def ref = getSingleSelection()
        if (ref == null) {
            return
        }
        int idx = indexOfIdentity(ref.tab.fields, ref.field)
        int newIdx = idx + direction
        if (idx < 0 || newIdx < 0 || newIdx >= ref.tab.fields.size()) {
            return
        }
        ref.tab.fields.remove(idx)
        ref.tab.fields.add(newIdx, ref.field)

        markDirty("Reihenfolge geändert")
        populateElementsTable()
        selectRowFor(ref.tab.tabTitle, ref.field.id)
        updateButtonStates()
    }

    private void moveFieldToIndex(def ref, int targetIndex) {
        int idx = indexOfIdentity(ref.tab.fields, ref.field)
        if (idx < 0) {
            return
        }
        int newIdx = targetIndex
        if (newIdx > idx) {
            newIdx--
        }
        if (newIdx < 0) {
            newIdx = 0
        }
        if (newIdx >= ref.tab.fields.size()) {
            newIdx = ref.tab.fields.size() - 1
        }
        if (newIdx == idx) {
            return
        }
        ref.tab.fields.remove(idx)
        ref.tab.fields.add(newIdx, ref.field)

        markDirty("Reihenfolge geändert")
        populateElementsTable()
        selectRowFor(ref.tab.tabTitle, ref.field.id)
        updateButtonStates()
    }

    // ------------------------------------------------------------------
    // Tab management
    // ------------------------------------------------------------------

    private String askForTabTitle(String title, String preset) {
        Object input = JOptionPane.showInputDialog(SCRIPTPANEL, "Name des Tabs:", title,
            JOptionPane.QUESTION_MESSAGE, null, null, preset)
        if (input == null) {
            return null
        }
        String name = input.toString().trim()
        if (name.isEmpty()) {
            setStatus("Der Tab-Name darf nicht leer sein.", COLOR_ERROR)
            return null
        }
        return name
    }

    private void addTab() {
        if (uiModel == null) {
            return
        }
        String name = askForTabTitle("Neuer Tab", "")
        if (name == null) {
            return
        }
        if (findTabByTitle(name) != null) {
            setStatus("Es gibt bereits einen Tab mit dem Namen '" + name + "'.", COLOR_ERROR)
            return
        }
        def newTab = new LinkedHashMap()
        newTab.put("tabTitle", name)
        newTab.put("fields", new ArrayList())
        uiModel.tabs.add(newTab)

        markDirty("Tab '" + name + "' angelegt")
        populateElementsTable()
        updateTabComboBox()
        selectTabInCombo(name)
        updateButtonStates()
    }

    private void renameTab() {
        if (uiModel == null || cmbTabSelect.getSelectedItem() == null) {
            return
        }
        String oldName = cmbTabSelect.getSelectedItem().toString()
        def tab = findTabByTitle(oldName)
        if (tab == null) {
            return
        }
        String name = askForTabTitle("Tab umbenennen", oldName)
        if (name == null || name.equals(oldName)) {
            return
        }
        if (findTabByTitle(name) != null) {
            setStatus("Es gibt bereits einen Tab mit dem Namen '" + name + "'.", COLOR_ERROR)
            return
        }
        tab.put("tabTitle", name)
        if (oldName.equals(editingTabTitle)) {
            editingTabTitle = name
        }

        markDirty("Tab in '" + name + "' umbenannt")
        populateElementsTable()
        updateTabComboBox()
        selectTabInCombo(name)
        if (editingFieldId != null) {
            selectRowFor(name, editingFieldId)
        }
        updateButtonStates()
    }

    private void moveTab(int direction) {
        if (uiModel == null || cmbTabSelect.getSelectedItem() == null) {
            return
        }
        String name = cmbTabSelect.getSelectedItem().toString()
        def tab = findTabByTitle(name)
        int idx = indexOfIdentity(uiModel.tabs, tab)
        int newIdx = idx + direction
        if (idx < 0 || newIdx < 0 || newIdx >= uiModel.tabs.size()) {
            return
        }
        uiModel.tabs.remove(idx)
        uiModel.tabs.add(newIdx, tab)

        markDirty("Tab-Reihenfolge geändert")
        populateElementsTable()
        updateTabComboBox()
        selectTabInCombo(name)
        updateButtonStates()
    }

    // ------------------------------------------------------------------
    // Status, dirty state, save / discard
    // ------------------------------------------------------------------

    private void setStatus(String text, Color color) {
        if (lblStatus == null) {
            return
        }
        lblStatus.setText(text)
        lblStatus.setForeground(color)
    }

    private void updateSettingsTabTitle() {
        if (tabbedPane == null || settingsPanel == null) {
            return
        }
        int idx = getSettingsTabIndex()
        if (idx >= 0) {
            tabbedPane.setTitleAt(idx, dirty ? "Einstellungen *" : "Einstellungen")
        }
    }

    private void markDirty(String action) {
        pendingChanges++
        dirty = true
        previewOutdated = true
        updateSettingsTabTitle()
        String msg = "● " + pendingChanges + (pendingChanges == 1 ? " ungespeicherte Änderung" : " ungespeicherte Änderungen")
        if (action != null) {
            msg = msg + "   —   " + action
        }
        setStatus(msg, COLOR_DIRTY)
        updateButtonStates()
    }

    private void setClean(String message, Color color) {
        dirty = false
        pendingChanges = 0
        previewOutdated = false
        updateSettingsTabTitle()
        setStatus(message, color)
        updateButtonStates()
    }

    private void updateButtonStates() {
        if (btnApply == null) {
            return
        }
        boolean hasModel = (uiModel != null)
        boolean hasTabs = hasModel && !uiModel.tabs.isEmpty()
        def single = getSingleSelection()
        int selCount = elementsTable == null ? 0 : elementsTable.getSelectedRowCount()

        btnApply.setEnabled(hasTabs)
        btnNewElement.setEnabled(hasModel)
        btnDelete.setEnabled(selCount > 0)
        btnDuplicate.setEnabled(single != null)

        int fieldIdx = single == null ? -1 : indexOfIdentity(single.tab.fields, single.field)
        btnUp.setEnabled(single != null && fieldIdx > 0)
        btnDown.setEnabled(single != null && fieldIdx >= 0 && fieldIdx < single.tab.fields.size() - 1)

        int tabIdx = cmbTabSelect == null ? -1 : cmbTabSelect.getSelectedIndex()
        btnTabNew.setEnabled(hasModel)
        btnTabRename.setEnabled(hasTabs && tabIdx >= 0)
        btnTabUp.setEnabled(hasTabs && tabIdx > 0)
        btnTabDown.setEnabled(hasTabs && tabIdx >= 0 && tabIdx < uiModel.tabs.size() - 1)

        btnSave.setEnabled(hasModel && dirty)
        btnDiscard.setEnabled(dirty && savedUiDefinition != null)
    }

    private void saveConfiguration() {
        if (uiModel == null) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Es ist keine gültige Konfiguration geladen.", "Fehler", JOptionPane.ERROR_MESSAGE)
            return
        }
        try {
            rebuildPreservingValues(false)
        } catch (Exception e) {
            JOptionPane.showMessageDialog(SCRIPTPANEL,
                "Die Oberfläche konnte nicht aufgebaut werden - es wurde nichts gespeichert.\n" + e.getMessage(),
                "Fehler", JOptionPane.ERROR_MESSAGE)
            setStatus("Nicht gespeichert: " + e.getMessage(), COLOR_ERROR)
            return
        }
        try {
            String json = getCurrentUiDefinition()
            ServerSettings.getInstance().setSetting(SETTING_KEY, json)
            savedUiDefinition = json
            setClean("✓ Gespeichert um " + new SimpleDateFormat("HH:mm").format(new Date()) +
                "  —  gilt für alle Akten und alle Nutzer", COLOR_OK)
        } catch (Exception e) {
            JOptionPane.showMessageDialog(SCRIPTPANEL, "Fehler beim Speichern: " + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE)
            setStatus("Nicht gespeichert: " + e.getMessage(), COLOR_ERROR)
        }
    }

    private void discardChanges() {
        if (savedUiDefinition == null) {
            return
        }
        int answer = GuiLib.askYesNo("Änderungen verwerfen?",
            "Alle " + pendingChanges + " noch nicht gespeicherten Änderungen werden verworfen\nund der zuletzt gespeicherte Stand wird geladen.\n\nFortfahren?")
        if (answer != JOptionPane.YES_OPTION) {
            return
        }
        if (!loadModelFromJson(savedUiDefinition)) {
            setStatus("Der gespeicherte Stand konnte nicht geladen werden: " + loadError, COLOR_ERROR)
            return
        }
        try {
            rebuildPreservingValues(false)
        } catch (Exception e) {
            setStatus("Oberfläche konnte nicht aufgebaut werden: " + e.getMessage(), COLOR_ERROR)
        }
        populateElementsTable()
        updateTabComboBox()
        prepareNewElement(null, null)
        setClean("Änderungen verworfen - gespeicherter Stand geladen.", Color.GRAY)
    }

    // ------------------------------------------------------------------
    // Settings tab
    // ------------------------------------------------------------------

    private JPanel buildSettingsPanel() {
        JPanel root = new JPanel(new BorderLayout(0, 5))
        root.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))

        // ---- upper half: tab toolbar + element table + row buttons ----
        JPanel topPanel = new JPanel(new BorderLayout(5, 5))

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2))
        tabBar.add(new JLabel("Konfigurierte Elemente"))
        tabBar.add(Box.createHorizontalStrut(15))
        btnTabNew = new JButton("Neuer Tab")
        btnTabNew.setToolTipText("Legt einen neuen, zunächst leeren Tab an")
        btnTabNew.setMnemonic(KeyEvent.VK_T)
        btnTabNew.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { addTab() }
        })
        btnTabRename = new JButton("Tab umbenennen")
        btnTabRename.setToolTipText("Benennt den im Formular gewählten Tab um")
        btnTabRename.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { renameTab() }
        })
        btnTabUp = new JButton("Tab ▲")
        btnTabUp.setToolTipText("Verschiebt den gewählten Tab nach vorn")
        btnTabUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { moveTab(-1) }
        })
        btnTabDown = new JButton("Tab ▼")
        btnTabDown.setToolTipText("Verschiebt den gewählten Tab nach hinten")
        btnTabDown.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { moveTab(1) }
        })
        tabBar.add(btnTabNew)
        tabBar.add(btnTabRename)
        tabBar.add(btnTabUp)
        tabBar.add(btnTabDown)
        topPanel.add(tabBar, BorderLayout.NORTH)

        tableModel = new DefaultTableModel(
            ['Tab', 'ID', 'Typ', 'Beschriftung', 'Platzhalter', 'Standard', 'Optionen'] as String[], 0) {
            public boolean isCellEditable(int row, int column) {
                return false
            }
        }
        elementsTable = new JTable(tableModel)
        elementsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
        elementsTable.setRowHeight(Math.max(elementsTable.getRowHeight(), 20))
        int[] widths = [120, 90, 140, 170, 110, 90, 170]
        for (int i = 0; i < widths.length; i++) {
            elementsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i])
        }
        elementsTable.setToolTipText("Doppelklick bearbeitet ein Element, Entf löscht, Alt+↑/Alt+↓ ändert die Reihenfolge")

        elementsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || suppressSelectionEvents) {
                    return
                }
                if (elementsTable.getSelectedRowCount() == 1) {
                    loadSelectedElementToForm()
                } else {
                    editingFieldId = null
                    editingTabTitle = null
                    btnApply.setText("✓ Element hinzufügen")
                }
                updateButtonStates()
            }
        })

        elementsTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && getSingleSelection() != null) {
                    txtElementLabel.requestFocusInWindow()
                }
            }
        })

        InputMap im = elementsTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        ActionMap am = elementsTable.getActionMap()
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "ff-delete")
        am.put("ff-delete", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { deleteSelectedElements() }
        })
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "ff-up")
        am.put("ff-up", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moveElement(-1) }
        })
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "ff-down")
        am.put("ff-down", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { moveElement(1) }
        })

        installRowReordering()

        JScrollPane tableScroll = new JScrollPane(elementsTable)
        tableScroll.setPreferredSize(new Dimension(700, 240))
        topPanel.add(tableScroll, BorderLayout.CENTER)

        JPanel rowButtons = new JPanel()
        rowButtons.setLayout(new BoxLayout(rowButtons, BoxLayout.Y_AXIS))
        btnDuplicate = new JButton("Duplizieren")
        btnDuplicate.setToolTipText("Legt eine Kopie des gewählten Elements direkt darunter an")
        btnDuplicate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { duplicateSelectedElement() }
        })
        btnUp = new JButton("Nach oben")
        btnUp.setToolTipText("Verschiebt das Element innerhalb seines Tabs nach oben (Alt+↑)")
        btnUp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { moveElement(-1) }
        })
        btnDown = new JButton("Nach unten")
        btnDown.setToolTipText("Verschiebt das Element innerhalb seines Tabs nach unten (Alt+↓)")
        btnDown.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { moveElement(1) }
        })
        btnDelete = new JButton("Löschen")
        btnDelete.setToolTipText("Entfernt die ausgewählten Elemente (Entf)")
        btnDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { deleteSelectedElements() }
        })
        [btnDuplicate, btnUp, btnDown, btnDelete].each { b ->
            b.setAlignmentX(Component.LEFT_ALIGNMENT)
            int buttonHeight = (int) b.getPreferredSize().getHeight()
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonHeight))
        }
        rowButtons.add(btnDuplicate)
        rowButtons.add(Box.createVerticalStrut(10))
        rowButtons.add(btnUp)
        rowButtons.add(btnDown)
        rowButtons.add(Box.createVerticalStrut(10))
        rowButtons.add(btnDelete)
        rowButtons.add(Box.createVerticalGlue())
        topPanel.add(rowButtons, BorderLayout.EAST)

        // ---- lower half: detail form ----
        JScrollPane formScroll = new JScrollPane(buildDetailForm())
        formScroll.setBorder(BorderFactory.createEmptyBorder())
        formScroll.getVerticalScrollBar().setUnitIncrement(16)

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, formScroll)
        split.setResizeWeight(0.5)
        split.setOneTouchExpandable(true)
        split.setBorder(BorderFactory.createEmptyBorder())
        root.add(split, BorderLayout.CENTER)

        // ---- status bar with the global actions ----
        root.add(buildStatusBar(), BorderLayout.SOUTH)

        return root
    }

    private JPanel buildDetailForm() {
        formPanel = new JPanel(new GridBagLayout())
        formPanel.setBorder(BorderFactory.createTitledBorder("Element hinzufügen / bearbeiten"))

        GridBagConstraints con = new GridBagConstraints()
        con.insets = new Insets(3, 5, 3, 5)
        int row = 0

        cmbTabSelect = new JComboBox()
        cmbTabSelect.setToolTipText("Tab, in dem das Element angezeigt wird")
        cmbTabSelect.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!updatingCombo) {
                    updateButtonStates()
                }
            }
        })
        addFormRow(formPanel, con, row++, 'tab', 'Tab:', cmbTabSelect, GridBagConstraints.LINE_START)

        cmbElementType = new JComboBox(ELEMENT_TYPE_MAP.values() as Object[])
        cmbElementType.setToolTipText("Art des Oberflächenelements")
        cmbElementType.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateFormForType()
                updatePlaceholderPreview()
            }
        })
        addFormRow(formPanel, con, row++, 'type', 'Typ:', cmbElementType, GridBagConstraints.LINE_START)

        ActionListener applyOnEnter = new ActionListener() {
            public void actionPerformed(ActionEvent e) { applyElement() }
        }

        txtElementLabel = new JTextField(30)
        txtElementLabel.setToolTipText("Text, der im Formular neben bzw. auf dem Element steht")
        txtElementLabel.addActionListener(applyOnEnter)
        txtElementLabel.getDocument().addDocumentListener(onTextChange({
            if (!idManuallyEdited && editingFieldId == null) {
                scheduleIdUpdate(deriveId(safeText(txtElementLabel), getSelectedType()))
            } else {
                schedulePreviewUpdate()
            }
        }))
        addFormRow(formPanel, con, row++, 'label', 'Beschriftung:', txtElementLabel, GridBagConstraints.LINE_START)

        JPanel placeholderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
        txtElementPlaceholder = new JTextField(20)
        txtElementPlaceholder.setToolTipText("Name des Platzhalters für Dokumentvorlagen. Leer lassen, um die ID zu verwenden.")
        txtElementPlaceholder.addActionListener(applyOnEnter)
        txtElementPlaceholder.getDocument().addDocumentListener(onTextChange({
            schedulePreviewUpdate()
        }))
        placeholderPanel.add(txtElementPlaceholder)
        placeholderPanel.add(Box.createHorizontalStrut(10))
        lblPlaceholderPreview = new JLabel(" ")
        lblPlaceholderPreview.setForeground(Color.GRAY)
        placeholderPanel.add(lblPlaceholderPreview)
        addFormRow(formPanel, con, row++, 'placeholder', 'Platzhalter:', placeholderPanel, GridBagConstraints.LINE_START)

        txtElementChoices = new JTextArea(4, 30)
        txtElementChoices.setLineWrap(false)
        txtElementChoices.setToolTipText("Eine Auswahlmöglichkeit pro Zeile - Kommas sind erlaubt")
        JScrollPane choicesScroll = new JScrollPane(txtElementChoices)
        choicesScroll.setPreferredSize(new Dimension(320, 80))
        addFormRow(formPanel, con, row++, 'choices', 'Auswahloptionen (eine pro Zeile):', choicesScroll, GridBagConstraints.FIRST_LINE_START)

        chkEmptyChoice = new JCheckBox("leere Auswahl an erster Stelle zulassen")
        chkEmptyChoice.setToolTipText("Ergänzt eine leere Option, damit das Feld auch unausgefüllt bleiben kann")
        addFormRow(formPanel, con, row++, 'emptychoice', '', chkEmptyChoice, GridBagConstraints.LINE_START)

        txtElementDefault = new JTextField(20)
        txtElementDefault.setToolTipText("Vorbelegung des Feldes in neuen Akten")
        txtElementDefault.addActionListener(applyOnEnter)
        addFormRow(formPanel, con, row++, 'defaultvalue', 'Standardwert:', txtElementDefault, GridBagConstraints.LINE_START)

        JPanel columnsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
        spnColumns = new JSpinner(new SpinnerNumberModel(0, 0, 200, 1))
        spnColumns.setToolTipText("Breite des Eingabefeldes in Zeichen")
        columnsPanel.add(spnColumns)
        columnsPanel.add(Box.createHorizontalStrut(8))
        JLabel lblColumnsHint = new JLabel("0 = automatisch")
        lblColumnsHint.setForeground(Color.GRAY)
        columnsPanel.add(lblColumnsHint)
        addFormRow(formPanel, con, row++, 'columns', 'Breite in Zeichen:', columnsPanel, GridBagConstraints.LINE_START)

        JPanel rowsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
        spnRows = new JSpinner(new SpinnerNumberModel(0, 0, 40, 1))
        spnRows.setToolTipText("Höhe des mehrzeiligen Textfeldes in Zeilen")
        rowsPanel.add(spnRows)
        rowsPanel.add(Box.createHorizontalStrut(8))
        JLabel lblRowsHint = new JLabel("0 = automatisch")
        lblRowsHint.setForeground(Color.GRAY)
        rowsPanel.add(lblRowsHint)
        addFormRow(formPanel, con, row++, 'rows', 'Höhe in Zeilen:', rowsPanel, GridBagConstraints.LINE_START)

        chkAdvanced = new JCheckBox("Erweitert")
        chkAdvanced.setToolTipText("Zeigt die technische ID und den Tooltip des Elements")
        chkAdvanced.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { updateFormForType() }
        })
        addFormRow(formPanel, con, row++, 'advancedtoggle', '', chkAdvanced, GridBagConstraints.LINE_START)

        advancedPanel = new JPanel(new GridBagLayout())
        advancedPanel.setBorder(BorderFactory.createTitledBorder("Erweitert"))
        GridBagConstraints acon = new GridBagConstraints()
        acon.insets = new Insets(3, 5, 3, 5)
        int arow = 0

        txtElementId = new JTextField(20)
        txtElementId.setToolTipText("Technische ID. Wird automatisch aus der Beschriftung erzeugt und muss eindeutig sein.")
        txtElementId.addActionListener(applyOnEnter)
        txtElementId.getDocument().addDocumentListener(onTextChange({
            if (!settingIdProgrammatically) {
                idManuallyEdited = true
            }
            schedulePreviewUpdate()
        }))
        addFormRow(advancedPanel, acon, arow++, 'id', 'ID (eindeutig):', txtElementId, GridBagConstraints.LINE_START)

        txtElementTooltip = new JTextField(30)
        txtElementTooltip.setToolTipText("Wird im Formular als Kurzhilfe angezeigt, wenn die Maus über dem Feld steht")
        txtElementTooltip.addActionListener(applyOnEnter)
        addFormRow(advancedPanel, acon, arow++, 'tooltip', 'Kurzhilfe (Tooltip):', txtElementTooltip, GridBagConstraints.LINE_START)

        addFormWideRow(formPanel, con, row++, 'advanced', advancedPanel)

        JPanel formButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0))
        btnNewElement = new JButton("Neues Element")
        btnNewElement.setToolTipText("Leert das Formular, um ein weiteres Element anzulegen")
        btnNewElement.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Object tab = cmbTabSelect.getSelectedItem()
                prepareNewElement(tab == null ? null : tab.toString(), getSelectedType())
            }
        })
        btnApply = new JButton("✓ Element hinzufügen")
        btnApply.setMnemonic(KeyEvent.VK_E)
        btnApply.setToolTipText("Übernimmt das Element in die Liste. Gespeichert wird erst mit 'Konfiguration speichern'.")
        btnApply.addActionListener(applyOnEnter)
        formButtons.add(btnNewElement)
        formButtons.add(btnApply)
        addFormWideRow(formPanel, con, row++, 'formbuttons', formButtons)

        // filler so the rows stay top-aligned
        con.gridx = 0
        con.gridy = row
        con.gridwidth = 2
        con.weightx = 1.0
        con.weighty = 1.0
        con.fill = GridBagConstraints.BOTH
        formPanel.add(new JLabel(""), con)
        con.gridwidth = 1

        return formPanel
    }

    private JPanel buildStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(10, 0))
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 2, 2, 2))

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0))
        lblStatus = new JLabel(" ")
        left.add(lblStatus)
        btnLoadDefault = new JButton("Standardkonfiguration laden")
        btnLoadDefault.setToolTipText("Ersetzt die fehlerhafte Konfiguration im Editor durch die Standardkonfiguration. "
            + "Der gespeicherte Wert auf dem Server bleibt unverändert, bis Sie speichern.")
        btnLoadDefault.setVisible(false)
        btnLoadDefault.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { loadDefaultConfiguration() }
        })
        left.add(btnLoadDefault)
        statusBar.add(left, BorderLayout.CENTER)

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0))
        btnDiscard = new JButton("Verwerfen")
        btnDiscard.setToolTipText("Verwirft alle noch nicht gespeicherten Änderungen")
        btnDiscard.setMnemonic(KeyEvent.VK_V)
        btnDiscard.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { discardChanges() }
        })
        btnSave = new JButton("Konfiguration speichern")
        btnSave.setToolTipText("Speichert die Konfiguration auf dem Server. Sie gilt für alle Akten und alle Nutzer.")
        btnSave.setMnemonic(KeyEvent.VK_S)
        btnSave.setFont(btnSave.getFont().deriveFont(Font.BOLD))
        btnSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { saveConfiguration() }
        })
        right.add(btnDiscard)
        right.add(btnSave)
        statusBar.add(right, BorderLayout.EAST)

        return statusBar
    }

    /** Drag and drop reordering of elements within one tab. */
    private void installRowReordering() {
        if (GraphicsEnvironment.isHeadless()) {
            return
        }
        elementsTable.setDragEnabled(true)
        elementsTable.setDropMode(DropMode.INSERT_ROWS)
        elementsTable.setTransferHandler(new TransferHandler() {

            public int getSourceActions(JComponent c) {
                return TransferHandler.MOVE
            }

            protected Transferable createTransferable(JComponent c) {
                int r = elementsTable.getSelectedRow()
                return new StringSelection("freeform-row:" + r)
            }

            public boolean canImport(TransferHandler.TransferSupport support) {
                return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor)
            }

            public boolean importData(TransferHandler.TransferSupport support) {
                if (!canImport(support)) {
                    return false
                }
                try {
                    String payload = support.getTransferable().getTransferData(DataFlavor.stringFlavor).toString()
                    if (!payload.startsWith("freeform-row:")) {
                        return false
                    }
                    int sourceRow = Integer.parseInt(payload.substring("freeform-row:".length()))
                    int dropRow = ((JTable.DropLocation) support.getDropLocation()).getRow()
                    if (sourceRow < 0 || sourceRow >= rowRefs.size()) {
                        return false
                    }
                    def sourceRef = rowRefs.get(sourceRow)

                    // determine the target position inside the source tab
                    def targetTab = null
                    int targetIndex = -1
                    if (dropRow >= rowRefs.size()) {
                        def lastRef = rowRefs.get(rowRefs.size() - 1)
                        targetTab = lastRef.tab
                        targetIndex = targetTab.fields.size()
                    } else {
                        def dropRef = rowRefs.get(dropRow)
                        targetTab = dropRef.tab
                        targetIndex = indexOfIdentity(targetTab.fields, dropRef.field)
                    }
                    if (!targetTab.is(sourceRef.tab)) {
                        setStatus("Elemente können nur innerhalb ihres Tabs verschoben werden. " +
                            "Für einen Tab-Wechsel das Element bearbeiten und den Tab umstellen.", COLOR_ERROR)
                        return false
                    }
                    moveFieldToIndex(sourceRef, targetIndex)
                    return true
                } catch (Exception e) {
                    return false
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // Help tab
    // ------------------------------------------------------------------

    private JPanel buildHelpPanel() {
        JPanel panel = new JPanel(new BorderLayout())
        JEditorPane help = new JEditorPane()
        help.setEditable(false)
        help.setContentType("text/html")
        help.setText(getHelpHtml())
        help.setCaretPosition(0)
        JScrollPane sp = new JScrollPane(help)
        sp.setPreferredSize(new Dimension(600, 400))
        sp.getVerticalScrollBar().setUnitIncrement(16)
        panel.add(sp, BorderLayout.CENTER)
        return panel
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
<b>Wichtig:</b> Die Oberflächen-Konfiguration ist eine <b>globale</b> Einstellung. Sie wirkt sich auf
<b>ALLE</b> Falldatenblätter dieses Typs in <b>allen Akten</b> und für <b>alle Nutzer</b> aus.
Bereits erfasste Daten bleiben erhalten, aber die Darstellung ändert sich für alle Akten.
</div>

<p>Mit diesem Plugin können Sie eigene Eingabemasken für Falldaten erstellen, ohne programmieren zu müssen.
Die Oberfläche wird über den Tab <b>Einstellungen</b> konfiguriert.</p>

<h2>Schnellstart</h2>
<ol>
<li>Wechseln Sie zum Tab <b>Einstellungen</b></li>
<li>Legen Sie mit <b>Neuer Tab</b> einen Tab an oder wählen Sie im Formular einen bestehenden aus</li>
<li>Wählen Sie den <b>Typ</b> und geben Sie die <b>Beschriftung</b> ein</li>
<li>Klicken Sie auf <b>✓ Element hinzufügen</b> - das Element erscheint in der Liste</li>
<li>Wiederholen Sie das für alle Felder; Tab und Typ bleiben dabei stehen</li>
<li>Klicken Sie zum Schluss auf <b>Konfiguration speichern</b></li>
</ol>

<h2>Die beiden Arbeitsschritte</h2>
<p>Der Editor trennt bewusst zwischen dem Bearbeiten der Liste und dem Speichern auf dem Server:</p>
<table>
<tr><th>Schritt</th><th>Button</th><th>Wirkung</th></tr>
<tr>
  <td>1. Liste bearbeiten</td>
  <td><b>✓ Element hinzufügen</b> bzw. <b>✓ Änderung übernehmen</b></td>
  <td>Ändert nur die Liste im Editor. Nichts wird auf dem Server geschrieben.</td>
</tr>
<tr>
  <td>2. Speichern</td>
  <td><b>Konfiguration speichern</b></td>
  <td>Schreibt die Konfiguration auf den Server - ab jetzt gilt sie für alle Akten und alle Nutzer.</td>
</tr>
</table>

<div class="hint">
<b>Woran erkenne ich ungespeicherte Änderungen?</b><br>
Am unteren Rand steht dann <code>● 3 ungespeicherte Änderungen</code>, und der Tab heißt
<b>Einstellungen *</b>. Mit <b>Verwerfen</b> kehren Sie zum zuletzt gespeicherten Stand zurück.
</div>

<div class="hint">
<b>Vorschau:</b> Sobald Sie den Tab <b>Einstellungen</b> verlassen, wird die Oberfläche automatisch
mit Ihren Änderungen neu aufgebaut - <b>ohne</b> zu speichern. Bereits eingegebene Werte bleiben dabei
erhalten. So können Sie Ihre Maske ausprobieren, bevor sie für alle gilt.
</div>

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

<p>Das Formular blendet immer nur die Eingaben ein, die für den gewählten Typ sinnvoll sind.
Für <b>Trennlinie</b> und <b>Leerzeile</b> müssen Sie deshalb gar nichts ausfüllen.</p>

<h2>Felder im Detail</h2>

<h3>Tab</h3>
<p>Bestimmt, in welchem Tab das Element erscheint. Tabs verwalten Sie über die Buttons oberhalb der Liste:
<b>Neuer Tab</b>, <b>Tab umbenennen</b>, <b>Tab ▲</b> und <b>Tab ▼</b>. Beim Umbenennen wandern alle
Elemente des Tabs automatisch mit.</p>

<h3>Beschriftung</h3>
<p>Der Text, der neben dem Eingabefeld angezeigt wird. Bei Kontrollkästchen steht er direkt am Kästchen,
bei einer Abschnittsüberschrift ist er die Überschrift selbst.</p>

<h3>Platzhalter</h3>
<p>Der Platzhalter wird in Dokumentvorlagen verwendet. Unter dem Eingabefeld sehen Sie sofort, wie der
fertige Platzhalter aussieht, zum Beispiel <code>{{FREEFORM_VORNAME}}</code>. Lassen Sie das Feld leer,
wird die ID verwendet.</p>
<div class="warning">
Vergeben Sie jeden Platzhalter nur <b>einmal</b>. Zwei Elemente mit demselben Platzhalter überschreiben
sich gegenseitig. Ändern Sie einen Platzhalter nachträglich, sind die dazu bereits erfassten Daten in den
Akten über den alten Namen nicht mehr erreichbar.
</div>

<h3>Auswahloptionen (nur bei Auswahlfeld)</h3>
<p>Geben Sie <b>eine Option pro Zeile</b> ein. Kommas innerhalb einer Option sind dadurch problemlos möglich.</p>
<p><b>Beispiel:</b></p>
<p><code>Herr<br>Frau<br>Divers<br>Firma</code></p>
<p>Mit der Option <b>leere Auswahl an erster Stelle zulassen</b> kann das Feld auch unausgefüllt bleiben.</p>

<h3>Standardwert</h3>
<p>Vorbelegung des Feldes. Bei Kontrollkästchen <code>ja</code> oder <code>nein</code>, bei Datumsfeldern
im Format <code>TT.MM.JJJJ</code>. Zahlenfelder ohne Standardwert bleiben leer und liefern keine
<code>0</code> in die Vorlage.</p>

<h3>Breite und Höhe</h3>
<p>Breite des Eingabefeldes in Zeichen bzw. Höhe eines mehrzeiligen Textfeldes in Zeilen.
<code>0</code> bedeutet automatisch.</p>

<h3>Erweitert: ID und Kurzhilfe</h3>
<p>Die <b>ID</b> wird automatisch aus der Beschriftung erzeugt und muss über alle Tabs hinweg eindeutig sein.
Sie müssen sie normalerweise nicht anfassen. Die <b>Kurzhilfe</b> erscheint im Formular als Tooltip,
wenn die Maus über dem Feld steht.</p>

<h2>Elemente bearbeiten</h2>
<ol>
<li>Klicken Sie ein Element in der Liste an - die Werte werden in das Formular geladen</li>
<li>Ändern Sie die gewünschten Werte</li>
<li>Klicken Sie auf <b>✓ Änderung übernehmen</b></li>
</ol>
<p>Sie können dabei auch die <b>ID ändern</b> (das Element wird umbenannt, es entsteht kein zweites) und den
<b>Tab wechseln</b> (Sie werden vor dem Verschieben gefragt). Mit <b>Neues Element</b> verlassen Sie den
Bearbeitungsmodus wieder.</p>

<h2>Liste bedienen</h2>
<table>
<tr><th>Aktion</th><th>Bedienung</th></tr>
<tr><td>Bearbeiten</td><td>Zeile anklicken oder doppelklicken</td></tr>
<tr><td>Mehrere auswählen</td><td>Strg- bzw. Umschalt-Taste beim Klicken</td></tr>
<tr><td>Löschen</td><td>Button <b>Löschen</b> oder Taste <b>Entf</b></td></tr>
<tr><td>Reihenfolge ändern</td><td><b>Nach oben</b> / <b>Nach unten</b>, <b>Alt+↑</b> / <b>Alt+↓</b> oder Ziehen mit der Maus</td></tr>
<tr><td>Kopie anlegen</td><td>Button <b>Duplizieren</b></td></tr>
</table>
<p>Die Reihenfolge lässt sich nur innerhalb eines Tabs ändern. Um ein Element in einen anderen Tab zu
verschieben, bearbeiten Sie es und stellen den Tab im Formular um.</p>

<h2>Buttons</h2>
<table>
<tr><th>Button</th><th>Funktion</th></tr>
<tr><td><b>Neuer Tab</b></td><td>Legt einen neuen, zunächst leeren Tab an</td></tr>
<tr><td><b>Tab umbenennen</b></td><td>Benennt den im Formular gewählten Tab um</td></tr>
<tr><td><b>Tab ▲ / Tab ▼</b></td><td>Ändert die Reihenfolge der Tabs</td></tr>
<tr><td><b>Duplizieren</b></td><td>Legt eine Kopie des gewählten Elements direkt darunter an</td></tr>
<tr><td><b>Nach oben / Nach unten</b></td><td>Ändert die Reihenfolge der Elemente</td></tr>
<tr><td><b>Löschen</b></td><td>Entfernt die ausgewählten Elemente</td></tr>
<tr><td><b>Neues Element</b></td><td>Leert das Formular, um ein weiteres Element anzulegen</td></tr>
<tr><td><b>✓ Element hinzufügen / Änderung übernehmen</b></td><td>Übernimmt das Element in die Liste (noch nicht gespeichert)</td></tr>
<tr><td><b>Verwerfen</b></td><td>Verwirft alle ungespeicherten Änderungen</td></tr>
<tr><td><b>Konfiguration speichern</b></td><td>Speichert die Konfiguration auf dem Server - gilt für alle Akten</td></tr>
</table>

<h2>Tipps</h2>
<ul>
<li>Strukturieren Sie Ihre Eingabemaske mit <b>Abschnittsüberschriften</b> und <b>Trennlinien</b></li>
<li>Verwenden Sie sprechende Platzhalter-Namen für eine einfache Zuordnung in Vorlagen</li>
<li>Nutzen Sie <b>Duplizieren</b>, wenn Sie mehrere ähnliche Felder brauchen</li>
<li>Nutzen Sie mehrere Tabs, um umfangreiche Formulare übersichtlich zu gliedern</li>
<li>Probieren Sie Änderungen über die automatische Vorschau aus, bevor Sie speichern</li>
</ul>

</body>
</html>'''
    }

    // ------------------------------------------------------------------
    // Assembly
    // ------------------------------------------------------------------

    private void loadDefaultConfiguration() {
        if (!loadModelFromJson(getDefaultJsonDefinition())) {
            setStatus("Die Standardkonfiguration konnte nicht geladen werden.", COLOR_ERROR)
            return
        }
        btnLoadDefault.setVisible(false)
        try {
            rebuildDynamicUi(false)
        } catch (Exception e) {
            setStatus("Oberfläche konnte nicht aufgebaut werden: " + e.getMessage(), COLOR_ERROR)
            return
        }
        populateElementsTable()
        updateTabComboBox()
        prepareNewElement(firstTabTitle(), "textbox")
        markDirty("Standardkonfiguration geladen - noch nicht gespeichert")
    }

    private String firstTabTitle() {
        if (uiModel == null || uiModel.tabs.isEmpty()) {
            return null
        }
        return uiModel.tabs.get(0).tabTitle
    }

    private ChangeListener buildPreviewListener() {
        def self = this
        return new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                self.onTabSelectionChanged()
            }
        }
    }

    /** Leaving the settings tab with pending changes rebuilds the preview - without saving. */
    private void onTabSelectionChanged() {
        if (rebuilding || !previewOutdated || uiModel == null) {
            return
        }
        if (tabbedPane.getSelectedComponent() == settingsPanel) {
            return
        }
        def self = this
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                self.runDeferredPreview()
            }
        })
    }

    private void runDeferredPreview() {
        if (rebuilding || !previewOutdated || uiModel == null) {
            return
        }
        int selIdx = tabbedPane.getSelectedIndex()
        String wanted = selIdx >= 0 ? tabbedPane.getTitleAt(selIdx) : null
        try {
            rebuildPreservingValues(false)
        } catch (Exception ex) {
            previewOutdated = false
            setStatus("Vorschau konnte nicht aufgebaut werden: " + ex.getMessage(), COLOR_ERROR)
            return
        }
        int idx = indexOfTabTitle(wanted)
        if (idx >= 0) {
            tabbedPane.setSelectedIndex(idx)
        }
    }

    public JPanel getUi() {

        SwingBuilder swing = new SwingBuilder()

        swing.edt {
            SCRIPTPANEL = new JPanel(new BorderLayout())
            tabbedPane = new JTabbedPane(JTabbedPane.LEFT)

            settingsPanel = buildSettingsPanel()
            helpPanel = buildHelpPanel()
            tabbedPane.addTab("Einstellungen", settingsPanel)
            tabbedPane.addTab("Hilfe", helpPanel)
            SCRIPTPANEL.add(tabbedPane, BorderLayout.CENTER)

            String uiDef = loadUiDefinition()
            if (loadModelFromJson(uiDef)) {
                savedUiDefinition = getCurrentUiDefinition()
                try {
                    rebuildDynamicUi(true)
                    setClean("Keine ungespeicherten Änderungen.", Color.GRAY)
                } catch (Exception e) {
                    setStatus("Die Oberfläche konnte nicht aufgebaut werden: " + e.getMessage(), COLOR_ERROR)
                }
                populateElementsTable()
                updateTabComboBox()
                prepareNewElement(firstTabTitle(), "textbox")
            } else {
                setStatus("Die gespeicherte Konfiguration ist fehlerhaft: " + loadError, COLOR_ERROR)
                btnLoadDefault.setVisible(true)
                updateButtonStates()
            }

            tabbedPane.addChangeListener(buildPreviewListener())
        }

        return SCRIPTPANEL
    }

}
