/*
* UVZ Export Falldatenblatt UI - finale Version
* Enthält umfassende UVZ und Teilnehmerdaten inkl. Organisationen
* Erstellt 26.08.2025, Autor: Ihr Name
*/

import groovy.swing.SwingBuilder
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.text.NumberFormat
import groovy.json.JsonOutput
import java.io.File
import java.io.FileWriter
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback

public class uvz_export_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    JPanel SCRIPTPANEL = null

    // UVZ Felder
    JTextField txtUvzNummer
    JComboBox cmbUvzMark

    // Operationsdaten
    JComboBox cmbOperationType
    JCheckBox chkUvzNumberNotAssigned
    JCheckBox chkStrictlyConfidential
    JFormattedTextField txtDeedDate
    JTextField txtDeedOfPerson
    JCheckBox chkNotaryRepresentative
    JTextField txtLocation

    // Geschäftsgegenstand Dropdown & Zusatztext
    JComboBox cmbBusinessPurpose
    JTextField txtAddonBusinessPurpose

    // Urkundenart Dropdown
    JComboBox cmbDeedType

    // Spezielle Checkboxen
    JCheckBox chkDepositedInheritanceContract
    JCheckBox chkRelevantForPublicArchives
    JCheckBox chkVideoCommunication
    JCheckBox chkWithDraft

    // Beteiligte dynamisch (mehrere)
    JPanel pnlParticipantsContainer
    int participantCount = 0

    // Hauptdokument Auswahl
    JButton btnSelectMainDocument
    File mainDocumentFile

    // Export Button
    JButton btnJsonExport

    FormPluginCallback callback
    NumberFormat betragFormat = NumberFormat.getInstance(Locale.GERMANY)
    SimpleDateFormat datumsFormat = new SimpleDateFormat("dd.MM.yyyy")
    SimpleDateFormat jsonDateFormat = new SimpleDateFormat("yyyy-MM-dd")

    public uvz_export_ui() {
        betragFormat.setMinimumFractionDigits(2)
        betragFormat.setMaximumFractionDigits(2)
    }

    public void setCallback(FormPluginCallback callback) {
        this.callback = callback
    }

    public JPanel getUi() {
        SwingBuilder swing = new SwingBuilder()
        swing.edt {
            SCRIPTPANEL = panel(size: [950, 950]) {
                tableLayout(cellpadding: 5) {

                    // UVZ Nummer
                    tr {
                        td(colspan: 4, align: 'center') {
                            panel(border: titledBorder(title: 'UVZ-Nummer')) {
                                tableLayout {
                                    tr {
                                        td { label(text: 'UVZ-Nummer:') }
                                        td { txtUvzNummer = textField(text: '/' + Calendar.getInstance().get(Calendar.YEAR), columns: 15) }
                                        td { label(text: 'Kennzeichen:') }
                                        td { cmbUvzMark = comboBox(items: ('A'..'Z').toArray(new String[0]), selectedIndex: 0) }
                                    }
                                }
                            }
                        }
                    }

                    // Operationsdaten inkl. Datum, Urkundperson, Vertreter, Ort
                    tr {
                        td(colspan: 4, align: 'center') {
                            panel(border: titledBorder(title: 'Aktion im Urkundenverzeichnis')) {
                                tableLayout {
                                    tr {
                                        td { label(text: 'Operationstyp:') }
                                        td {
                                            cmbOperationType = comboBox(items: [
                                                'ADD_DEED_ENTRY',
                                                'UPDATE_DEED_ENTRY',
                                                'DELETE_DEED_ENTRY',
                                                'ARCHIVE_DEED_ENTRY',
                                                'RESTORE_DEED_ENTRY'
                                            ], selectedIndex: 0)
                                        }
                                        td { label(text: 'Datum des Amtsgeschäfts:') }
                                        td {
                                            txtDeedDate = formattedTextField(format: datumsFormat, columns: 10, text: datumsFormat.format(new Date()))
                                            button(text: '', icon: new ImageIcon(getClass().getResource('/icons/schedule.png')),
                                                    actionPerformed: { GuiLib.dateSelector(txtDeedDate, true) })
                                        }
                                    }
                                    tr {
                                        td { label(text: 'Urkundperson:') }
                                        td { txtDeedOfPerson = textField(text: 'Karcher, Sascha', columns: 20) }
                                        td { chkNotaryRepresentative = checkBox(text: 'Vertreter') }
                                        td { chkUvzNumberNotAssigned = checkBox(text: 'UVZ-Nummer nicht zugewiesen') }
                                    }
                                    tr {
                                        td { label(text: 'Ort der Beurkundung:') }
                                        td { txtLocation = textField(text: 'Geschäftsstelle', columns: 20) }
                                        td { chkStrictlyConfidential = checkBox(text: 'Streng vertraulich') }
                                        td { label(text: '') }
                                    }
                                }
                            }
                        }
                    }

                    // Geschäftsgegenstand Dropdown + Zusatz
                    tr {
                        td(colspan: 4, align: 'center') {
                            panel(border: titledBorder(title: 'Geschäftsgegenstand')) {
                                tableLayout {
                                    tr {
                                        td {
                                            cmbBusinessPurpose = comboBox(items: [
                                                'Adoptionsantrag',
                                                'Anfechtung der Annahme einer Erbschaft',
                                                'Anfechtung der Erbausschlagung',
                                                'Antrag auf Erteilung eines Erbscheins (mit EV)',
                                                'Antrag auf Erteilung eines Europäischen Nachlasszeugnisses (mit EV)',
                                                'Antrag auf Erteilung eines Testamentsvollstreckerzeugnisses',
                                                'Auflassung',
                                                'Ausgliederung',
                                                'Bauträgervertrag',
                                                'Beteiligungsvertrag',
                                                'Betreuungsverfügung/Patientenverfügung',
                                                'Dienstbarkeitsbestellung',
                                                'Ehevertrag',
                                                'Ehe- und Erbvertrag',
                                                'Eidesstattliche Versicherung',
                                                'Einbringungsvertrag',
                                                'Erbauseinandersetzungsvertrag',
                                                'Erbausschlagung',
                                                'Erbbaurechtsvertrag',
                                                'Erbteilsübertragung',
                                                'Erbvertrag',
                                                'Erb-/Pflichtteils-/Zuwendungsverzichtsvertrag',
                                                'Genehmigung/Vollmachtsbestätigung',
                                                'Geschäftsanteilsübertragungsvertrag',
                                                'Geschäftsanteilsverpfändung',
                                                'Gesellschafterbeschluss',
                                                'Gesellschafterliste',
                                                'Gesellschaftervereinbarung',
                                                'Grundbuchberichtigungsantrag',
                                                'Grundschuld-/Hypothekenbestellung (mit ZV-Unterwerfung)',
                                                'Grundschuld-/Hypothekenbestellung (ohne ZV-Unterwerfung)',
                                                'Gründung einer Gesellschaft',
                                                'Hauptversammlungsbeschluss',
                                                'Hofübergabe',
                                                'Identitätserklärung',
                                                'Kaufvertrag',
                                                'Erbbaurechtskaufvertrag',
                                                'Erbteilskaufvertrag',
                                                'Geschäftsanteilskaufvertrag',
                                                'Grundstückskaufvertrag',
                                                'Grundstückskaufvertrag (Angebot)',
                                                'Grundstückskaufvertrag (Annahme)',
                                                'Unternehmenskaufvertrag',
                                                'Wohnungs-/Teileigentumskaufvertrag',
                                                'Löschungsbewilligung',
                                                'Messungsanerkennung und Auflassung',
                                                'Miteigentümervereinbarung',
                                                'Nachlassverzeichnis',
                                                'Nachtrag/Änderungsurkunde',
                                                'Nießbrauchsbestellung',
                                                'Prioritätsverhandlung',
                                                'Registeranmeldung',
                                                'Genossenschaftsregisteranmeldung',
                                                'Gesellschaftsregisteranmeldung',
                                                'Handelsregisteranmeldung',
                                                'Partnerschaftsregisteranmeldung',
                                                'Vereinsregisteranmeldung',
                                                'Rücktritt vom Erbvertrag/Widerruf eines gemeinschaftlichen Testaments',
                                                'Satzungsbescheinigung',
                                                'Scheidungsfolgenvereinbarung',
                                                'Schenkungsvertrag',
                                                'Schuldanerkenntnis/Schuldversprechen',
                                                'Sorgeerklärung',
                                                'Spaltungsvertrag/Spaltungsplan',
                                                'Tauschvertrag',
                                                'Geschäftsanteilstauschvertrag',
                                                'Grundstückstauschvertrag',
                                                'Wohnungs-/Teileigentumstauschvertrag',
                                                'Testament (Einzel-)',
                                                'Testament (gemeinschaftlich)',
                                                'Teilungserklärung/Teilungsvertrag nach WEG',
                                                'Treuhandvertrag',
                                                'Überlassungsvertrag',
                                                'Überlassungsvertrag (Grundstück)',
                                                'Überlassungsvertrag (Wohnungs-/Teileigentum)',
                                                'Übernahmeerklärung (zur Kapitalerhöhung)',
                                                'Umwandlungsbeschluss',
                                                'Vaterschaftsanerkennung',
                                                'Vermächtniserfüllungsvertrag',
                                                'Vermögensauseinandersetzung',
                                                'Verschmelzungsvertrag',
                                                'Vollmacht',
                                                'Registervollmacht',
                                                'Vorsorgevollmacht',
                                                'Vorkaufsrechtsbestellung',
                                                'Zustimmung',
                                                'Löschungszustimmung',
                                                'Verwalterzustimmung',
                                                'Sonstiges:'
                                            ])
                                        }
                                    }
                                    tr {
                                        td { label(text: 'Zusatzgeschäftsgegenstand:') }
                                        td { txtAddonBusinessPurpose = textField(columns: 30) }
                                    }
                                }
                            }
                        }
                    }

                    // Urkundenart Dropdown
                    tr {
                        td(colspan: 4, align: 'center') {
                            panel(border: titledBorder(title: 'Urkundenart')) {
                                tableLayout {
                                    tr {
                                        td {
                                            cmbDeedType = comboBox(items: [
                                                'Begl. von Unterschriften, Handzeichen oder qeS ohne Anfertigung eines Urkundenentwurfs',
                                                'Begl. von Unterschriften, Handzeichen oder qeS mit Anfertigung eines Urkundenentwurfs',
                                                'Bescheinigungen des Notars',
                                                'Verfügungen von Todes wegen',
                                                'Vermittlungen von Auseinandersetzungen/Beurkundungen und Beschlüsse nach SachenRBerG',
                                                '(Sonstige) Beurkundungen und Beschlüsse'
                                            ])
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Spezielle Checkboxen
                    tr {
                        td(colspan: 4) {
                            panel {
                                chkDepositedInheritanceContract = checkBox(text: 'Notariell verwahrter Erbvertrag')
                                chkRelevantForPublicArchives = checkBox(text: 'Relevant für öffentliche Archive', selected: true)
                                chkVideoCommunication = checkBox(text: 'Videokommunikation', selected: true)
                                chkWithDraft = checkBox(text: 'Vollzugsentwurf (§ 2 Abs. 2 Satz 2 UA-GebS)', selected: true)
                            }
                        }
                    }

                    // Beteiligte Dynamisch
                    tr {
                        td(colspan: 4, align: 'center') {
                            panel(border: titledBorder(title: 'Beteiligte')) {
                                pnlParticipantsContainer = panel(layout: new BoxLayout(swing.panel(), BoxLayout.Y_AXIS))
                                button(text: 'Beteiligten hinzufügen', actionPerformed: {
                                    addParticipant()
                                })
                            }
                        }
                    }

                    // Dokumente
                    tr {
                        td(colspan: 4, align: 'center') {
                            panel(border: titledBorder(title: 'Dokumente')) {
                                tableLayout {
                                    tr {
                                        td {
                                            btnSelectMainDocument = button(text: 'Hauptdokument wählen', actionPerformed: {
                                                JFileChooser fc = new JFileChooser()
                                                if (fc.showOpenDialog(SCRIPTPANEL) == JFileChooser.APPROVE_OPTION) {
                                                    mainDocumentFile = fc.getSelectedFile()
                                                }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Export
                    tr {
                        td(colspan: 4, align: 'center') {
                            btnJsonExport = button(text: 'UVZ Export', actionPerformed: {
                                exportToZip()
                            })
                        }
                    }
                }
            }
        }
        return SCRIPTPANEL
    }

    // Dynamische Teilnehmer hinzufügen
    private int participantCount = 0
    private void addParticipant() {
        participantCount++
        JPanel participantPanel = new JPanel()
        participantPanel.layout = new BoxLayout(participantPanel, BoxLayout.Y_AXIS)
        participantPanel.border = BorderFactory.createTitledBorder("Beteiligter " + participantCount)

        JTextField txtFirstNames = new JTextField(20)
        JTextField txtSurname = new JTextField(20)
        JTextField txtBirthDate = new JTextField(10)
        JComboBox cmbProxyRole = new JComboBox(['Keine Rolle', 'Vertreter/in', 'Vertretene/r'])
        JComboBox cmbParticipantType = new JComboBox(['Natürliche Person', 'Organisation'])

        participantPanel.add(new JLabel("Vorname(n):"))
        participantPanel.add(txtFirstNames)
        participantPanel.add(new JLabel("Nachname:"))
        participantPanel.add(txtSurname)
        participantPanel.add(new JLabel("Geburtsdatum (dd.MM.yyyy):"))
        participantPanel.add(txtBirthDate)
        participantPanel.add(new JLabel("Proxy Rolle:"))
        participantPanel.add(cmbProxyRole)
        participantPanel.add(new JLabel("Personentyp:"))
        participantPanel.add(cmbParticipantType)

        // Organisationfelder für juristische Person (kann erweitert werden)

        pnlParticipantsContainer.add(participantPanel)
        pnlParticipantsContainer.revalidate()
        pnlParticipantsContainer.repaint()
    }

    // Hilfsmethoden und Export-Funktion

    private Map parseUvzNumber(String uvzText) {
        def result = [counter: 0, year: Calendar.getInstance().get(Calendar.YEAR)]
        if (uvzText && !uvzText.trim().empty) {
            def matcher = (uvzText =~ /(\\d+)\\/(\\d{4})/)
            if (matcher.matches()) {
                result.counter = Integer.parseInt(matcher[0][1])
                result.year = Integer.parseInt(matcher[0][2])
            }
        }
        return result
    }

    private String toIsoDate(String germanDate) {
        try {
            return jsonDateFormat.format(datumsFormat.parse(germanDate))
        } catch (Exception e) {
            return ""
        }
    }

    private void exportToZip() {
        try {
            def uvz = parseUvzNumber(txtUvzNummer.text)
            String folderName = "${uvz.counter}_${uvz.year}_${cmbUvzMark.selectedItem}"
            File dir = new File(folderName)
            if(!dir.exists()) { dir.mkdirs() }

            def participants = []
            pnlParticipantsContainer.components.each { JPanel p ->
                JTextField fn = p.getComponent(1) // Vorname
                JTextField sn = p.getComponent(3) // Nachname
                JTextField bd = p.getComponent(5) // Geburtsdatum
                JComboBox pr = p.getComponent(7) // ProxyRole
                JComboBox pt = p.getComponent(9) // ParticipantType

                def participantJson = [
                    firstNames: fn.getText(),
                    surname: sn.getText(),
                    birthDate: toIsoDate(bd.getText()),
                    proxyRole: pr.getSelectedItem() == 'Keine Rolle' ? null : pr.getSelectedItem(),
                    participantType: pt.getSelectedItem()
                ]
                participants << participantJson
            }

            def jsonData = [
                uvzNr: [
                    year: uvz.year,
                    counter: uvz.counter,
                    mark: cmbUvzMark.selectedItem
                ],
                operationData: [
                    operationType: cmbOperationType.selectedItem,
                    uvzNumberNotAssigned: chkUvzNumberNotAssigned.isSelected(),
                    strictlyConfidential: chkStrictlyConfidential.isSelected(),
                    deedDate: toIsoDate(txtDeedDate.text),
                    deedOfPerson: txtDeedOfPerson.text,
                    notaryRepresentative: chkNotaryRepresentative.isSelected(),
                    location: txtLocation.text
                ],
                businessPurpose: cmbBusinessPurpose.selectedItem,
                addonBusinessPurpose: txtAddonBusinessPurpose.text,
                deedType: cmbDeedType.selectedItem,
                depositedInheritanceContract: chkDepositedInheritanceContract.isSelected(),
                relevantForPublicArchives: chkRelevantForPublicArchives.isSelected(),
                videoCommunication: chkVideoCommunication.isSelected(),
                withDraft: chkWithDraft.isSelected(),
                participants: participants,
                mainDocumentMetaData: [
                    documentReference: [
                        fileReference: mainDocumentFile ? mainDocumentFile.name : ''
                    ]
                ]
            ]

            File jsonFile = new File(dir, 'uvz_export.json')
            jsonFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(jsonData))

            if(mainDocumentFile != null) {
                mainDocumentFile.withInputStream { is ->
                    new File(dir, mainDocumentFile.name).withOutputStream { os ->
                        os << is
                    }
                }
            }

            JOptionPane.showMessageDialog(SCRIPTPANEL,"UVZ Export erfolgreich im Ordner '${dir.absolutePath}' gespeichert!","Export abgeschlossen",JOptionPane.INFORMATION_MESSAGE)
        } catch(Exception e) {
            JOptionPane.showMessageDialog(SCRIPTPANEL,"Fehler beim Export: ${e.message}","Export Fehler",JOptionPane.ERROR_MESSAGE)
        }
    }

}