import groovy.swing.SwingBuilder
import java.awt.*
import javax.swing.*
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import java.util.ArrayList
import java.text.SimpleDateFormat
import java.util.Calendar
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback
import com.jdimension.jlawyer.client.settings.ClientSettings
import com.jdimension.jlawyer.services.JLawyerServiceLocator

public class uwg02_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    JPanel SCRIPTPANEL=null;
    FormPluginCallback callback=null;
    JSpinner spnYear = null;
    JEditorPane reportPane = null;
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
    String lastGeneratedHtml = null;

    public uwg02_ui() {
        super();
    }

    public String getAsHtml() {
        return GuiLib.getAsHtml(this.SCRIPTPANEL);
    }

    public ArrayList<String> getPlaceHolders(String prefix) {
        ArrayList<String> placeHolders=FormsLib.getPlaceHolders(prefix, this.SCRIPTPANEL);
        return placeHolders;
    }

    public Hashtable getPlaceHolderValues(String prefix) {
        Hashtable placeHolders=FormsLib.getPlaceHolderValues(prefix, this.SCRIPTPANEL);
        return placeHolders;
    }

    public Hashtable getPlaceHolderDescriptions(String prefix) {
        Hashtable placeHolders=FormsLib.getPlaceHolderDescriptions(prefix, this.SCRIPTPANEL);
        return placeHolders;
    }

    public void setPlaceHolderValues(String prefix, Hashtable placeHolderValues) {
        FormsLib.setPlaceHolderValues(prefix, placeHolderValues, this.SCRIPTPANEL);
    }

    public void setCallback(FormPluginCallback callback) {
        this.callback=callback;
    }

    public JPanel getUi() {

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        SwingBuilder swing=new SwingBuilder()
        swing.edt {
            SCRIPTPANEL=panel(size: [300, 300]) {

                vbox {
                    tabPaneMain = tabbedPane(id: 'tabs', tabPlacement: JTabbedPane.LEFT) {
                        panel(name: 'Einstellungen') {
                            tableLayout (cellpadding: 5) {
                                tr {
                                    td {
                                        label(text: 'Berichtsjahr:')
                                    }
                                    td {
                                        spnYear = spinner(
                                            model: new SpinnerNumberModel(currentYear, 2020, 2050, 1)
                                        )
                                        spnYear.setEditor(new JSpinner.NumberEditor(spnYear, "#"))
                                    }
                                }
                            }
                        }
                        panel(name: 'Report') {
                            borderLayout()
                            panel(constraints: BorderLayout.NORTH) {
                                button(text: 'Aktualisieren', actionPerformed: { generateReport() })
                                button(text: 'In Akte speichern', actionPerformed: { saveReportToCase() })
                            }
                            scrollPane(constraints: BorderLayout.CENTER) {
                                reportPane = editorPane(
                                    contentType: 'text/html',
                                    editable: false,
                                    text: '<html><body><p>Klicken Sie auf "Aktualisieren" um den Report zu generieren.</p></body></html>'
                                )
                            }
                        }
                    }
                }
            }
        }

        return SCRIPTPANEL;
    }

    private void generateReport() {
        try {
            ClientSettings settings = ClientSettings.getInstance();
            JLawyerServiceLocator locator = JLawyerServiceLocator.getInstance(settings.getLookupProperties());
            def formsService = locator.lookupFormsServiceRemote();

            def allForms = formsService.getFormsByType("uwg01");

            int selectedYear = spnYear.getValue();
            def groupedAll = new LinkedHashMap(); // Verfahrensart -> [Abschlussart -> count]
            def groupedMitglied = new LinkedHashMap(); // filtered: _GEGENMITGLIED checked
            def groupedVsw = new LinkedHashMap(); // filtered: _VSW checked

            allForms.each { form ->
                def entries = formsService.getFormEntries(form.id)

                // Convert List<ArchiveFileFormEntriesBean> to Map
                def entryMap = [:]
                entries.each { entry ->
                    entryMap[entry.entryKey] = entry.stringValue
                }

                // Filter: check if Einleitung is in selected year
                // Find keys ending with the field names (prefix-independent)
                def einleitungStr = findValueBySuffix(entryMap, "_EINLEITUNG")
                if (einleitungStr) {
                    try {
                        def einleitungDate = dateFormat.parse(einleitungStr)
                        def cal = Calendar.getInstance()
                        cal.setTime(einleitungDate)
                        int formYear = cal.get(Calendar.YEAR)
                        if (formYear == selectedYear) {
                            // Group by Verfahrensart -> Abschlussart
                            def verfahrensart = findValueBySuffix(entryMap, "_VERFAHRENSART") ?: "(leer)"
                            def abschluss = findValueBySuffix(entryMap, "_ABSCHLUSS") ?: "(leer)"

                            // Add to groupedAll (all forms)
                            if (!groupedAll[verfahrensart]) groupedAll[verfahrensart] = new LinkedHashMap()
                            groupedAll[verfahrensart][abschluss] = (groupedAll[verfahrensart][abschluss] ?: 0) + 1

                            // Add to groupedMitglied if checkbox is checked
                            def gegenMitglied = findValueBySuffix(entryMap, "_GEGENMITGLIED")
                            if (isCheckboxSelected(gegenMitglied)) {
                                if (!groupedMitglied[verfahrensart]) groupedMitglied[verfahrensart] = new LinkedHashMap()
                                groupedMitglied[verfahrensart][abschluss] = (groupedMitglied[verfahrensart][abschluss] ?: 0) + 1
                            }

                            // Add to groupedVsw if checkbox is checked
                            def vsw = findValueBySuffix(entryMap, "_VSW")
                            if (isCheckboxSelected(vsw)) {
                                if (!groupedVsw[verfahrensart]) groupedVsw[verfahrensart] = new LinkedHashMap()
                                groupedVsw[verfahrensart][abschluss] = (groupedVsw[verfahrensart][abschluss] ?: 0) + 1
                            }
                        }
                    } catch (Exception e) {
                        // Skip forms with invalid date
                    }
                }
            }

            def generatedDate = new SimpleDateFormat("dd.MM.yyyy, HH:mm").format(new Date())

            StringBuilder html = new StringBuilder()
            html.append("""<html>
<head>
</head>
<body style="font-family: Arial, sans-serif; margin: 0; padding: 10px;">

<table width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom: 20px;">
    <tr>
        <td style="border-bottom: 3px solid #1e3c72; padding: 15px 0;">
            <font size="+2" color="#1e3c72"><b>UWG-Auswertung</b></font><br>
            <font size="-1" color="#555">Berichtsjahr: <b>${selectedYear}</b> &nbsp;&nbsp;&nbsp; Erstellt am: ${generatedDate} Uhr</font>
        </td>
    </tr>
</table>
""")

            // Section 1: Geschäftsbericht (alle)
            appendSectionHeader(html, "Geschäftsbericht ${selectedYear}")
            appendGroupedData(html, groupedAll, selectedYear)

            // Section 2: Geschäftsbericht (eigene Mitglieder)
            appendSectionHeader(html, "Geschäftsbericht ${selectedYear} (eigene Mitglieder)")
            appendGroupedData(html, groupedMitglied, selectedYear)

            // Section 3: Geschäftsbericht (VSW)
            appendSectionHeader(html, "Geschäftsbericht ${selectedYear} (VSW)")
            appendGroupedData(html, groupedVsw, selectedYear)

            html.append("</body></html>")
            lastGeneratedHtml = html.toString()
            reportPane.setText(lastGeneratedHtml)
            reportPane.setCaretPosition(0) // Scroll to top

        } catch (Exception e) {
            lastGeneratedHtml = null
            reportPane.setText("<html><body><p style='color:red;'>Fehler beim Generieren des Reports: ${escapeHtml(e.getMessage())}</p></body></html>")
        }
    }

    private void saveReportToCase() {
        if (lastGeneratedHtml == null) {
            javax.swing.JOptionPane.showMessageDialog(null, "Bitte generieren Sie zuerst einen Report.", "Kein Report vorhanden", javax.swing.JOptionPane.WARNING_MESSAGE)
            return
        }

        String caseId = callback.getCaseId()
        if (caseId == null || caseId.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(null, "Der Report kann nicht gespeichert werden, da keine Akte geöffnet ist.", "Keine Akte", javax.swing.JOptionPane.WARNING_MESSAGE)
            return
        }

        try {
            int selectedYear = spnYear.getValue()
            String fileName = "UWG-Auswertung_${selectedYear}.html"
            byte[] content = lastGeneratedHtml.getBytes("UTF-8")
            StorageLib.addDocument(caseId, fileName, content)
            javax.swing.JOptionPane.showMessageDialog(null, "Der Report wurde als '${fileName}' in der Akte gespeichert.", "Gespeichert", javax.swing.JOptionPane.INFORMATION_MESSAGE)
        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(null, "Fehler: " + e.getMessage(), "Fehler beim Speichern", javax.swing.JOptionPane.ERROR_MESSAGE)
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private String findValueBySuffix(Map entryMap, String suffix) {
        def key = entryMap.keySet().find { it.endsWith(suffix) }
        return key ? entryMap[key] : null
    }

    private boolean isCheckboxSelected(String value) {
        if (value == null) return false
        def v = value.trim().toLowerCase()
        return v == "1" || v == "true" || v == "ja" || v == "x" || v == "yes"
    }

    private void appendSectionHeader(StringBuilder html, String title) {
        html.append("""
<table width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-top: 25px;">
    <tr>
        <td bgcolor="#1e3c72" style="padding: 12px 15px;">
            <font size="+1" color="#ffffff"><b>${escapeHtml(title)}</b></font>
        </td>
    </tr>
</table>
""")
    }

    private void appendGroupedData(StringBuilder html, Map grouped, int selectedYear) {
        if (grouped.isEmpty()) {
            html.append("<p style='text-align: center; color: #666; font-style: italic; padding: 20px;'><i>Keine Verfahren im Berichtsjahr ${selectedYear} gefunden.</i></p>")
        } else {
            grouped.each { verfahrensart, abschluesse ->
                html.append("<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-top: 15px; margin-bottom: 10px;'>")
                html.append("<tr><td style='border-bottom: 2px solid #1e3c72; padding-bottom: 5px;'><font size='+0' color='#1e3c72'><b>${escapeHtml(verfahrensart.toString())}</b></font></td></tr>")
                html.append("</table>")

                html.append("<table width='100%' cellpadding='8' cellspacing='0' border='0'>")
                html.append("<tr bgcolor='#f0f4f8'><td style='border-bottom: 2px solid #d0d7e2;'><font color='#1e3c72'><b>Abschlussart</b></font></td><td align='right' width='100' style='border-bottom: 2px solid #d0d7e2;'><font color='#1e3c72'><b>Anzahl</b></font></td></tr>")
                int gesamtVerfahrensart = 0
                abschluesse.each { abschluss, count ->
                    html.append("<tr><td style='border-bottom: 1px solid #e8ecf1;'>${escapeHtml(abschluss.toString())}</td><td align='right' style='border-bottom: 1px solid #e8ecf1;'>${count}</td></tr>")
                    gesamtVerfahrensart += count
                }
                html.append("<tr bgcolor='#e8f4f8'><td style='border-top: 2px solid #1e3c72;'><font color='#1e3c72'><b>Gesamt</b></font></td><td align='right' style='border-top: 2px solid #1e3c72;'><font color='#1e3c72'><b>${gesamtVerfahrensart}</b></font></td></tr>")
                html.append("</table>")
            }
        }
        html.append("<br>")
    }
}
