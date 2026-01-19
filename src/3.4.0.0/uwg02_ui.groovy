import groovy.swing.SwingBuilder
import java.awt.*
import javax.swing.*
import javax.swing.JPanel
import javax.swing.JTabbedPane
import java.util.ArrayList
import com.jdimension.jlawyer.client.plugins.form.FormPluginCallback

public class uwg02_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods {

    JPanel SCRIPTPANEL=null;
    FormPluginCallback callback=null;

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

        SwingBuilder swing=new SwingBuilder()
        swing.edt {
            SCRIPTPANEL=panel(size: [300, 300]) {

                vbox {
                    tabPaneMain = tabbedPane(id: 'tabs', tabPlacement: JTabbedPane.LEFT) {
                        panel(name: 'Einstellungen') {
                            tableLayout (cellpadding: 5) {
                            }
                        }
                        panel(name: 'Report') {
                            tableLayout (cellpadding: 5) {
                            }
                        }
                    }
                }
            }
        }

        return SCRIPTPANEL;
    }
}
