/*
 * Falldatenblatt Fluggastrechte
 * EU-VO 261/2004, Montrealer Uebereinkommen, Ticketerstattung
 * Autor: Sebastian Schwaebe
 * Version: 1.1.0
 */

import groovy.swing.SwingBuilder
import javax.swing.SwingConstants
import javax.swing.JPanel
import javax.swing.JLabel
import java.util.ArrayList
import java.text.NumberFormat
import java.text.DecimalFormat
import java.util.Hashtable
import java.util.Locale
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback

public class fluggastrechte01_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    JPanel SCRIPTPANEL = null
    FormPluginCallback callback = null

    NumberFormat betragFormat = NumberFormat.getInstance(Locale.GERMANY).getNumberInstance()

    // =====================================================================
    // RVG-GEBUEHRENTABELLE (Anlage 2 zu § 13 Abs. 1 RVG)
    // Stand: 2025 (gueltig ab 01.01.2025)
    //
    // AKTUALISIERUNG: Wenn der Gesetzgeber die Gebuehrentabelle aendert,
    // einfach die Werte in dieser Liste anpassen.
    // Format: [Obergrenze Gegenstandswert, einfache Gebuehr]
    // =====================================================================
    private static final double[][] RVG_TABELLE = [
        [500, 51.50], [1000, 93.00], [1500, 134.50], [2000, 176.00],
        [3000, 235.50], [4000, 295.00], [5000, 354.50], [6000, 414.00],
        [7000, 473.50], [8000, 533.00], [9000, 592.50], [10000, 652.00],
        [13000, 707.00], [16000, 762.00], [19000, 817.00], [22000, 872.00],
        [25000, 927.00], [30000, 1013.00], [35000, 1099.00], [40000, 1185.00],
        [45000, 1271.00], [50000, 1357.00], [65000, 1456.50], [80000, 1556.00],
        [95000, 1655.50], [110000, 1755.00], [125000, 1854.50], [140000, 1954.00],
        [155000, 2053.50], [170000, 2153.00], [185000, 2252.50], [200000, 2352.00],
        [230000, 2492.00], [260000, 2632.00], [290000, 2772.00], [320000, 2912.00],
        [350000, 3052.00], [380000, 3192.00], [410000, 3332.00], [440000, 3472.00],
        [470000, 3612.00], [500000, 3752.00]
    ]

    // Einfache Gebuehr aus RVG-Tabelle ermitteln
    private double getEinfacheGebuehr(double gegenstandswert) {
        if (gegenstandswert <= 0) return 0.0
        for (double[] zeile : RVG_TABELLE) {
            if (gegenstandswert <= zeile[0]) return zeile[1]
        }
        // Ueber 500.000: je weitere 50.000 EUR + 175 EUR
        double gebuehr = 3752.0
        for (double i = 500000; i < gegenstandswert; i += 50000) {
            gebuehr += 175.0
        }
        return gebuehr
    }

    public fluggastrechte01_ui() {
        super()
        betragFormat.setMaximumFractionDigits(2)
        betragFormat.setMinimumFractionDigits(2)
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
        FormsLib.setPlaceHolderValues(prefix, placeHolderValues, this.SCRIPTPANEL)
    }

    public void setCallback(FormPluginCallback callback) {
        this.callback = callback
    }

    // Berechnet EU-261-Pauschale anhand Distanz (km) und ob Flug innerhalb EU
    private String berechneEU261Pauschale(String distanzStr, String streckentyp, String verspaetungStr) {
        try {
            double distanz = Double.parseDouble(distanzStr.replace(",", ".").replaceAll("[^0-9.]", ""))
            double verspaetung = 0
            try { verspaetung = Double.parseDouble(verspaetungStr.replace(",", ".")) } catch(e) {}

            // Bei Verspaetung: Anspruch besteht nur ab 3h
            // Bei Annullierung/Nichtbeforderung: immer
            double betrag = 0
            if (distanz <= 1500) {
                betrag = 250
            } else if (distanz <= 3500) {
                betrag = 400
            } else {
                // Ueber 3500 km: 400 innerhalb EU, 600 ausserhalb
                if (streckentyp == "innerhalb EU (> 3500 km)") {
                    betrag = 400
                } else {
                    betrag = 600
                }
            }
            return String.valueOf((int) betrag)
        } catch (Exception e) {
            return ""
        }
    }

    public JPanel getUi() {

        SwingBuilder swing = new SwingBuilder()
        swing.edt {
            SCRIPTPANEL = panel(size: [300, 300]) {
                tableLayout(cellpadding: 5) {

                    // =========================================================
                    // ABSCHNITT 1: FLUGDATEN
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '1. Flugdaten')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Airline:') }
                                        td {
                                            textField(
                                                id: 'tfAirline',
                                                name: '_AIRLINE',
                                                clientPropertyJlawyerdescription: 'Name der Airline',
                                                columns: 30, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Flugnummer:') }
                                        td {
                                            textField(
                                                id: 'tfFlugnummer',
                                                name: '_FLUGNR',
                                                clientPropertyJlawyerdescription: 'Flugnummer (z.B. LH1234)',
                                                columns: 15, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Flugdatum:') }
                                        td {
                                            textField(
                                                id: 'tfFlugdatum',
                                                name: '_FLUGDATUM',
                                                clientPropertyJlawyerdescription: 'Flugdatum (TT.MM.JJJJ)',
                                                columns: 12, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Abflughafen:') }
                                        td {
                                            textField(
                                                id: 'tfAbflughafen',
                                                name: '_ABFLUGHAFEN',
                                                clientPropertyJlawyerdescription: 'Abflughafen (Name und IATA-Code, z.B. Frankfurt FRA)',
                                                columns: 30, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Zielflughafen:') }
                                        td {
                                            textField(
                                                id: 'tfZielflughafen',
                                                name: '_ZIELFLUGHAFEN',
                                                clientPropertyJlawyerdescription: 'Zielflughafen (Name und IATA-Code, z.B. London LHR)',
                                                columns: 30, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Buchungsreferenz (PNR):') }
                                        td {
                                            textField(
                                                id: 'tfBuchungsref',
                                                name: '_BUCHUNGSREF',
                                                clientPropertyJlawyerdescription: 'Buchungsreferenz / PNR',
                                                columns: 15, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Gebuchte Klasse:') }
                                        td {
                                            comboBox(
                                                items: ['', 'Economy', 'Premium Economy', 'Business', 'First'],
                                                name: '_KLASSE',
                                                clientPropertyJlawyerdescription: 'Gebuchte Reiseklasse',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Anzahl Passagiere (Mandanten):') }
                                        td {
                                            comboBox(
                                                items: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'],
                                                id: 'cbAnzahlPassagiere',
                                                name: '_ANZAHL_PASSAGIERE',
                                                clientPropertyJlawyerdescription: 'Anzahl der Passagiere / Mandanten',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Namen weiterer Passagiere:') }
                                        td {
                                            scrollPane {
                                                textArea(
                                                    id: 'taWeiterePassagiere',
                                                    name: '_WEITERE_PASSAGIERE',
                                                    clientPropertyJlawyerdescription: 'Namen der weiteren Passagiere (falls mehrere Mandanten)',
                                                    lineWrap: true, wrapStyleWord: true, columns: 30, rows: 3, editable: true
                                                )
                                            }
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Ticketpreis gesamt (EUR):') }
                                        td {
                                            formattedTextField(
                                                id: 'nTicketpreis',
                                                name: '_TICKETPREIS',
                                                clientPropertyJlawyerdescription: 'Gezahlter Ticketpreis gesamt (EUR)',
                                                format: betragFormat, text: '0,00', columns: 10
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 2: STOERUNG / EREIGNIS
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '2. Art der Störung')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Art der Störung:') }
                                        td {
                                            comboBox(
                                                items: ['', 'Verspätung', 'Annullierung', 'Nichtbeförderung (Denied Boarding)', 'Herabstufung (Downgrading)'],
                                                name: '_STOERUNG_ART',
                                                clientPropertyJlawyerdescription: 'Art der Flugstörung',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Geplante Abflugzeit:') }
                                        td {
                                            textField(
                                                name: '_GEPL_ABFLUG',
                                                clientPropertyJlawyerdescription: 'Geplante Abflugzeit (TT.MM.JJJJ HH:MM)',
                                                columns: 18, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Tatsächliche Abflugzeit:') }
                                        td {
                                            textField(
                                                name: '_TATS_ABFLUG',
                                                clientPropertyJlawyerdescription: 'Tatsächliche Abflugzeit (TT.MM.JJJJ HH:MM)',
                                                columns: 18, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Geplante Ankunftszeit am Ziel:') }
                                        td {
                                            textField(
                                                name: '_GEPL_ANKUNFT',
                                                clientPropertyJlawyerdescription: 'Geplante Ankunftszeit am Zielflughafen (TT.MM.JJJJ HH:MM)',
                                                columns: 18, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Tatsächliche Ankunftszeit am Ziel:') }
                                        td {
                                            textField(
                                                name: '_TATS_ANKUNFT',
                                                clientPropertyJlawyerdescription: 'Tatsächliche Ankunftszeit am Zielflughafen (TT.MM.JJJJ HH:MM)',
                                                columns: 18, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Verspätung am Zielort (Stunden):') }
                                        td {
                                            textField(
                                                id: 'tfVerspaetungH',
                                                name: '_VERSPAETUNG_H',
                                                clientPropertyJlawyerdescription: 'Verspätungsdauer am Zielort in Stunden (z.B. 4,5)',
                                                columns: 8, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Genannter Grund der Airline:') }
                                        td {
                                            comboBox(
                                                items: [
                                                    '',
                                                    'Technische Probleme',
                                                    'Schlechtes Wetter',
                                                    'Streik',
                                                    'Außergewöhnliche Umstände (allgemein)',
                                                    'Verspäteter Zubringerflug',
                                                    'Überbucht',
                                                    'Sicherheitsbedenken',
                                                    'Kein Grund genannt'
                                                ],
                                                name: '_GRUND_AIRLINE',
                                                clientPropertyJlawyerdescription: 'Von der Airline genannter Grund der Störung',
                                                editable: true
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Außergewöhnliche Umstände geltend gemacht?') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja', 'nein', 'unklar'],
                                                name: '_AUSSERGEWOEHNL',
                                                clientPropertyJlawyerdescription: 'Hat die Airline außergewöhnliche Umstände (Art. 5 III VO 261) geltend gemacht?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Ersatzbeförderung angeboten?') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja, angenommen', 'ja, abgelehnt', 'nein'],
                                                name: '_ERSATZBEFOERD',
                                                clientPropertyJlawyerdescription: 'Wurde eine Ersatzbeförderung durch die Airline angeboten?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Betreuungsleistungen erhalten? (Art. 9 VO 261)') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja, vollständig', 'ja, teilweise', 'nein'],
                                                name: '_BETREUUNG',
                                                clientPropertyJlawyerdescription: 'Wurden Mahlzeiten, Getränke, Hotelunterkunft etc. bereitgestellt?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td(colfill: true, valign: 'TOP') {
                                            label(text: 'Schilderung des Sachverhalts:')
                                        }
                                        td {
                                            scrollPane {
                                                textArea(
                                                    name: '_SACHVERHALT',
                                                    clientPropertyJlawyerdescription: 'Detaillierte Schilderung des Sachverhalts',
                                                    lineWrap: true, wrapStyleWord: true, columns: 50, rows: 6, editable: true
                                                )
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 3: EU-VO 261/2004 – AUSGLEICHSZAHLUNG
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '3. EU-VO 261/2004 – Ausgleichszahlung')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Flugdistanz (km):') }
                                        td {
                                            textField(
                                                id: 'tfDistanz',
                                                name: '_DISTANZ_KM',
                                                clientPropertyJlawyerdescription: 'Flugdistanz in km (Großkreisentfernung)',
                                                columns: 10, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Streckentyp:') }
                                        td {
                                            comboBox(
                                                items: [
                                                    'bis 1500 km',
                                                    '1500 bis 3500 km',
                                                    'innerhalb EU (> 3500 km)',
                                                    'außerhalb EU (> 3500 km)'
                                                ],
                                                id: 'cbStreckentyp',
                                                name: '_STRECKENTYP',
                                                clientPropertyJlawyerdescription: 'Streckentyp für die Berechnung der EU261-Pauschale',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: '') }
                                        td {
                                            button(
                                                text: '▶ EU-261-Pauschale berechnen',
                                                actionPerformed: {
                                                    def distanz = tfDistanz.text
                                                    def streckentyp = cbStreckentyp.selectedItem?.toString() ?: ''
                                                    def verspaetung = tfVerspaetungH.text
                                                    def pauschale = berechneEU261Pauschale(distanz, streckentyp, verspaetung)
                                                    tfEU261Pauschale.text = pauschale
                                                    // Gesamtbetrag aktualisieren
                                                    try {
                                                        int anz = Integer.parseInt(cbAnzahlPassagiere.selectedItem?.toString() ?: '1')
                                                        int p = Integer.parseInt(pauschale)
                                                        tfEU261Gesamt.text = String.valueOf(anz * p)
                                                    } catch(e) {}
                                                }
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'EU-261-Pauschale je Passagier (EUR):') }
                                        td {
                                            textField(
                                                id: 'tfEU261Pauschale',
                                                name: '_EU261_PAUSCHALE',
                                                clientPropertyJlawyerdescription: 'EU-261-Ausgleichszahlung je Passagier in EUR (250/400/600)',
                                                columns: 8, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'EU-261-Gesamtforderung (EUR):') }
                                        td {
                                            textField(
                                                id: 'tfEU261Gesamt',
                                                name: '_EU261_GESAMT',
                                                clientPropertyJlawyerdescription: 'EU-261-Ausgleichszahlung gesamt (Pauschale × Anzahl Passagiere)',
                                                columns: 10, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'EU-261-Anspruch bereits geltend gemacht?') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja', 'nein'],
                                                name: '_EU261_GELTENDGEMACHT',
                                                clientPropertyJlawyerdescription: 'Hat Mandant EU-261-Anspruch bereits selbst geltend gemacht?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Antwort der Airline auf bisherige Geltendmachung:') }
                                        td {
                                            comboBox(
                                                items: [
                                                    '',
                                                    'Keine Antwort',
                                                    'Ablehnung wegen außergewöhnlicher Umstände',
                                                    'Ablehnung ohne Begründung',
                                                    'Teilzahlung angeboten',
                                                    'Voucher angeboten',
                                                    'Zahlung zugesagt, aber nicht geleistet'
                                                ],
                                                name: '_EU261_ANTWORT_AIRLINE',
                                                clientPropertyJlawyerdescription: 'Reaktion der Airline auf bisherige Geltendmachung',
                                                editable: true
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 4: MONTREALER UEBEREINKOMMEN (GEPAECK)
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '4. Montrealer Übereinkommen – Gepäck')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Gepäckschaden vorhanden?') }
                                        td {
                                            comboBox(
                                                items: ['nein', 'ja'],
                                                name: '_MK_GEPAECK_JA',
                                                clientPropertyJlawyerdescription: 'Ist ein Gepäckschaden (Verlust, Beschädigung, Verspätung) entstanden?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Art des Gepäckschadens:') }
                                        td {
                                            comboBox(
                                                items: ['', 'Verlust', 'Beschädigung', 'Verspätete Herausgabe'],
                                                name: '_MK_GEPAECK_ART',
                                                clientPropertyJlawyerdescription: 'Art des Gepäckschadens nach MK',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Schadenshöhe Gepäck (EUR):') }
                                        td {
                                            formattedTextField(
                                                name: '_MK_GEPAECK_SCHADEN',
                                                clientPropertyJlawyerdescription: 'Schadenshöhe Gepäck in EUR',
                                                format: betragFormat, text: '0,00', columns: 10
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'PIR (Property Irregularity Report) erstattet?') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja', 'nein'],
                                                name: '_MK_PIR',
                                                clientPropertyJlawyerdescription: 'Wurde am Flughafen eine Schadensanzeige (PIR) erstattet?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Datum PIR / Schadensanzeige:') }
                                        td {
                                            textField(
                                                name: '_MK_PIR_DATUM',
                                                clientPropertyJlawyerdescription: 'Datum der PIR-Erstellung (TT.MM.JJJJ)',
                                                columns: 12, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Schriftliche Reklamation binnen 7/21 Tagen?') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja (Beschädigung: 7 Tage)', 'ja (Verspätung: 21 Tage)', 'nein', 'nicht relevant (Totalverlust)'],
                                                name: '_MK_REKLAMATION_FRIST',
                                                clientPropertyJlawyerdescription: 'Wurde Schadensanzeige fristgerecht nach Art. 31 MK gestellt?',
                                                editable: true
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 5: TICKETERSTATTUNG
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '5. Ticketerstattung')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Ticketerstattung beansprucht?') }
                                        td {
                                            comboBox(
                                                items: ['nein', 'ja'],
                                                name: '_ERSTATTUNG_JA',
                                                clientPropertyJlawyerdescription: 'Wird Erstattung des Ticketpreises verlangt?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Erstattung bereits beantragt?') }
                                        td {
                                            comboBox(
                                                items: ['', 'ja', 'nein'],
                                                name: '_ERSTATTUNG_BEANTRAGT',
                                                clientPropertyJlawyerdescription: 'Hat Mandant Ticketerstattung bereits beantragt?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Reaktion der Airline auf Erstattungsantrag:') }
                                        td {
                                            comboBox(
                                                items: ['', 'Keine Reaktion', 'Abgelehnt', 'Voucher angeboten', 'Teilweise erstattet', 'Vollständig erstattet'],
                                                name: '_ERSTATTUNG_ANTWORT',
                                                clientPropertyJlawyerdescription: 'Reaktion der Airline auf Erstattungsantrag',
                                                editable: true
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 6: GESAMTFORDERUNG UND FRISTEN
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '6. Gesamtforderung & Fristen')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Gesamtforderung (EUR):') }
                                        td {
                                            formattedTextField(
                                                name: '_GESAMTFORDERUNG',
                                                clientPropertyJlawyerdescription: 'Gesamtforderungsbetrag in EUR (alle Ansprüche)',
                                                format: betragFormat, text: '0,00', columns: 10
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Gesetzte Zahlungsfrist bis:') }
                                        td {
                                            textField(
                                                name: '_ZAHLUNGSFRIST',
                                                clientPropertyJlawyerdescription: 'Im Schreiben gesetzte Zahlungsfrist (TT.MM.JJJJ)',
                                                columns: 12, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Verjährungsende EU-261 (3 Jahre):') }
                                        td {
                                            textField(
                                                name: '_VERJAEHRUNG_EU261',
                                                clientPropertyJlawyerdescription: 'Verjährungsdatum für EU-261-Ansprüche (3 Jahre ab Flugdatum)',
                                                columns: 12, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Bereits außergerichtlich geltend gemacht am:') }
                                        td {
                                            textField(
                                                name: '_AUSSERGER_DATUM',
                                                clientPropertyJlawyerdescription: 'Datum des außergerichtlichen Anschreibens durch RA',
                                                columns: 12, text: ''
                                            )
                                        }
                                    }

                                    tr {
                                        td(colfill: true, valign: 'TOP') {
                                            label(text: 'Interne Notizen:')
                                        }
                                        td {
                                            scrollPane {
                                                textArea(
                                                    name: '_NOTIZEN',
                                                    clientPropertyJlawyerdescription: 'Interne Notizen (erscheinen nicht im Dokument)',
                                                    lineWrap: true, wrapStyleWord: true, columns: 50, rows: 4, editable: true
                                                )
                                            }
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 7: BELEHRUNG UND VORAUSSETZUNGEN RA-KOSTEN
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '7. Belehrung & Voraussetzungen RA-Kosten')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Belehrung nach Art. 14 VO 261/2004:') }
                                        td {
                                            comboBox(
                                                items: ['nein, nicht belehrt', 'ja, schriftlich belehrt', 'unklar'],
                                                id: 'cbBelehrung',
                                                name: '_BELEHRUNG_ART14',
                                                clientPropertyJlawyerdescription: 'Wurden die Passagiere von der Airline schriftlich ueber ihre Rechte nach VO 261/2004 belehrt?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Bereits ohne Anwalt geltend gemacht?') }
                                        td {
                                            comboBox(
                                                items: ['nein', 'ja'],
                                                id: 'cbSelbstGeltend',
                                                name: '_SELBST_GELTENDGEMACHT',
                                                clientPropertyJlawyerdescription: 'Hat der Mandant die Ansprueche bereits selbst (ohne Anwalt) erfolglos geltend gemacht?',
                                                editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'RA-Kosten erstattungsfähig:') }
                                        td {
                                            textField(
                                                id: 'tfRAKostenErstattbar',
                                                name: '_RA_KOSTEN_ERSTATTBAR',
                                                clientPropertyJlawyerdescription: 'Ob RA-Kosten von der Airline zu erstatten sind (ja/nein)',
                                                columns: 30, text: '', editable: false
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }

                    // =========================================================
                    // ABSCHNITT 8: RECHTSANWALTSGEBUEHREN (RVG)
                    // =========================================================
                    tr {
                        td(colfill: true, align: 'left') {
                            panel(border: titledBorder(title: '8. Rechtsanwaltsgebühren (RVG)')) {
                                tableLayout(cellpadding: 5) {

                                    tr {
                                        td { label(text: 'Gegenstandswert (EUR):') }
                                        td {
                                            formattedTextField(
                                                id: 'tfRAGegenstandswert',
                                                name: '_RA_GEGENSTANDSWERT',
                                                clientPropertyJlawyerdescription: 'Gegenstandswert fuer die RA-Gebuehrenberechnung (in der Regel = Gesamtforderung)',
                                                format: betragFormat, text: '0,00', columns: 10
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: '') }
                                        td {
                                            button(
                                                text: '▶ RVG-Gebühren berechnen',
                                                actionPerformed: {
                                                    try {
                                                        // Gegenstandswert parsen
                                                        double gw = Double.parseDouble(tfRAGegenstandswert.text.replace(".", "").replace(",", ".").replaceAll("[^0-9.]", ""))
                                                        int anzahl = Integer.parseInt(cbAnzahlPassagiere.selectedItem?.toString() ?: '1')

                                                        // Einfache Gebuehr ermitteln
                                                        double einfach = getEinfacheGebuehr(gw)

                                                        // Geschaeftsgebuehr Nr. 2300 VV RVG (1,3)
                                                        double geschaeft = einfach * 1.3

                                                        // Erhoehung Nr. 1008 VV RVG (0,3 je weiteren Auftraggeber)
                                                        double erhoehung = 0.0
                                                        if (anzahl > 1) {
                                                            erhoehung = einfach * 0.3 * (anzahl - 1)
                                                        }

                                                        // Auslagenpauschale Nr. 7002 VV RVG
                                                        double auslagen = 20.0

                                                        // Netto
                                                        double netto = geschaeft + erhoehung + auslagen

                                                        // USt 19%
                                                        double ust = netto * 0.19

                                                        // Gesamt
                                                        double gesamt = netto + ust

                                                        // Felder befuellen
                                                        tfRAEinfach.text = betragFormat.format(einfach)
                                                        tfRAGeschaeft.text = betragFormat.format(geschaeft)
                                                        tfRAErhoehung.text = betragFormat.format(erhoehung)
                                                        tfRAAuslagen.text = betragFormat.format(auslagen)
                                                        tfRANetto.text = betragFormat.format(netto)
                                                        tfRAUst.text = betragFormat.format(ust)
                                                        tfRAGesamt.text = betragFormat.format(gesamt)

                                                        // RA-Kosten erstattbar pruefen
                                                        def belehrung = cbBelehrung.selectedItem?.toString() ?: ''
                                                        def selbst = cbSelbstGeltend.selectedItem?.toString() ?: ''
                                                        if (belehrung == 'nein, nicht belehrt' || selbst == 'ja') {
                                                            tfRAKostenErstattbar.text = 'ja'
                                                        } else if (belehrung == 'ja, schriftlich belehrt' && selbst == 'nein') {
                                                            tfRAKostenErstattbar.text = 'nein'
                                                        } else {
                                                            tfRAKostenErstattbar.text = 'prüfen'
                                                        }
                                                    } catch (Exception ex) {
                                                        tfRAGesamt.text = 'Fehler: ' + ex.message
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Einfache Gebühr (§ 13 RVG):') }
                                        td {
                                            textField(
                                                id: 'tfRAEinfach',
                                                name: '_RA_EINFACHE_GEBUEHR',
                                                clientPropertyJlawyerdescription: 'Einfache Wertgebuehr nach Anlage 2 zu § 13 RVG',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: '1,3 Geschäftsgebühr (Nr. 2300 VV):') }
                                        td {
                                            textField(
                                                id: 'tfRAGeschaeft',
                                                name: '_RA_GESCHAEFTSGEBUEHR',
                                                clientPropertyJlawyerdescription: '1,3 Geschaeftsgebuehr nach Nr. 2300 VV RVG',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Erhöhung Nr. 1008 VV RVG:') }
                                        td {
                                            textField(
                                                id: 'tfRAErhoehung',
                                                name: '_RA_ERHOEHUNG_1008',
                                                clientPropertyJlawyerdescription: 'Erhoehungsgebuehr Nr. 1008 VV RVG (0,3 je weiteren Auftraggeber)',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Auslagenpauschale (Nr. 7002 VV):') }
                                        td {
                                            textField(
                                                id: 'tfRAAuslagen',
                                                name: '_RA_AUSLAGEN',
                                                clientPropertyJlawyerdescription: 'Auslagenpauschale Nr. 7002 VV RVG (20,00 EUR)',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'Zwischensumme netto:') }
                                        td {
                                            textField(
                                                id: 'tfRANetto',
                                                name: '_RA_NETTO',
                                                clientPropertyJlawyerdescription: 'RA-Gebuehren netto (Geschaeftsgebuehr + Erhoehung + Auslagen)',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: '19% Umsatzsteuer:') }
                                        td {
                                            textField(
                                                id: 'tfRAUst',
                                                name: '_RA_UST',
                                                clientPropertyJlawyerdescription: 'Umsatzsteuer 19% auf RA-Gebuehren',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                    tr {
                                        td { label(text: 'RA-Gebühren gesamt (brutto):') }
                                        td {
                                            textField(
                                                id: 'tfRAGesamt',
                                                name: '_RA_GESAMT',
                                                clientPropertyJlawyerdescription: 'RA-Gebuehren gesamt brutto',
                                                columns: 10, text: '', editable: false
                                            )
                                        }
                                    }

                                }
                            }
                        }
                    }

                }
            }
        }
        return SCRIPTPANEL
    }

}
