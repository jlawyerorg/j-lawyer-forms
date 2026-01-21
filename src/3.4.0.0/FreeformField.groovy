public class FreeformField {

    private String id = null
    private String tabTitle = null
    private String label = null
    private String type = null
    private String placeHolder = null
    private ArrayList<String> choices = new ArrayList<>()

    public FreeformField() {
        super()
    }

    public String getPlaceHolderName() {
        if (this.placeHolder != null && this.placeHolder.length() > 0) {
            return "_" + this.placeHolder.toUpperCase().replaceAll("[^A-Z0-9_]", "_")
        } else if (this.id != null && this.id.length() > 0) {
            return "_" + this.id.toUpperCase().replaceAll("[^A-Z0-9_]", "_")
        }
        return "_FIELD"
    }

    public void setId(String id) {
        this.id = id
    }

    public String getId() {
        return this.id
    }

    public void setTabTitle(String tabTitle) {
        this.tabTitle = tabTitle
    }

    public String getTabTitle() {
        return this.tabTitle
    }

    public void setLabel(String label) {
        this.label = label
    }

    public String getLabel() {
        return this.label
    }

    public void setType(String type) {
        this.type = type
    }

    public String getType() {
        return this.type
    }

    public void setPlaceHolder(String placeHolder) {
        this.placeHolder = placeHolder
    }

    public String getPlaceHolder() {
        return this.placeHolder
    }

    public ArrayList<String> getChoices() {
        return this.choices
    }

    public void setChoices(ArrayList<String> choices) {
        this.choices = choices
    }

    public void addChoice(String choice) {
        this.choices.add(choice)
    }
}
