import groovy.swing.SwingBuilder
import java.awt.*
import javax.swing.*
import javax.swing.JPanel
import javax.swing.JTabbedPane
import java.util.ArrayList
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback

public class freeform01_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    JPanel SCRIPTPANEL=null;
    FormPluginCallback callback=null;

    public freeform01_ui() {
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

        SwingBuilder swing=new SwingBuilder()

        ButtonGroup radioGroup = new ButtonGroup()

        swing.edt {
            SCRIPTPANEL=panel(size: [300, 300]) {

                vbox {
                    tabPaneMain = tabbedPane(id: 'tabs', tabPlacement: JTabbedPane.LEFT) {
                        panel(name: 'Beispieltab') {
                            tableLayout (cellpadding: 5) {
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: 'Textfeld')
                                    }
                                    td (colfill:true, align: 'left') {
                                        textField(name: "_TEXTFELD", clientPropertyJlawyerdescription: "Textfeld", text: '', columns: 30)
                                    }
                                }
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: 'Mehrzeiliges Textfeld')
                                    }
                                    td (colfill:true, align: 'left') {
                                        scrollPane(preferredSize: [300, 100]) {
                                            textArea(name: "_TEXTAREA", clientPropertyJlawyerdescription: "Mehrzeiliges Textfeld", lineWrap: true, wrapStyleWord: true, columns: 50, rows: 4, editable: true)
                                        }
                                    }
                                }
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: 'Auswahlfeld')
                                    }
                                    td (colfill:true, align: 'left') {
                                        comboBox(items: ['', 'Option 1', 'Option 2', 'Option 3'], name: "_AUSWAHL", clientPropertyJlawyerdescription: "Auswahlfeld", editable: true)
                                    }
                                }
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: '')
                                    }
                                    td (colfill:true, align: 'left') {
                                        checkBox(text: 'Kontrollkästchen', name: "_CHECKBOX", clientPropertyJlawyerdescription: "Kontrollkästchen", selected: false)
                                    }
                                }
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: 'Optionsfelder')
                                    }
                                    td (colfill:true, align: 'left') {
                                        panel {
                                            vbox {
                                                rbOption1 = radioButton(text: 'Option A', name: "_RADIO_A", clientPropertyJlawyerdescription: "Option A", buttonGroup: radioGroup)
                                                rbOption2 = radioButton(text: 'Option B', name: "_RADIO_B", clientPropertyJlawyerdescription: "Option B", buttonGroup: radioGroup)
                                                rbOption3 = radioButton(text: 'Option C', name: "_RADIO_C", clientPropertyJlawyerdescription: "Option C", buttonGroup: radioGroup)
                                            }
                                        }
                                    }
                                }
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: 'Zahlenfeld')
                                    }
                                    td (colfill:true, align: 'left') {
                                        spinner(id: 'nZahlenfeld', name: "_SPINNER", clientPropertyJlawyerdescription: "Zahlenfeld", model: spinnerNumberModel(minimum: 0, maximum: 1000, value: 0, stepSize: 1))
                                    }
                                }
                            }
                        }
                        panel(name: 'Einstellungen') {
                            tableLayout (cellpadding: 5) {
                                tr {
                                    td (colfill:true, align: 'left') {
                                        label(text: 'Beispieleinstellung')
                                    }
                                    td (colfill:true, align: 'left') {
                                        textField(name: "_EINSTELLUNG", clientPropertyJlawyerdescription: "Beispieleinstellung", text: '', columns: 30)
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }

        return SCRIPTPANEL;

    }

}
