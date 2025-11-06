# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the j-lawyer.org plugin repository for form plugins. Plugins can be deployed without updating the j-lawyer.org client or server. Forms are written in Groovy and provide UI components and data capture functionality for the j-lawyer legal case management system.

## Architecture

### Version-Based Directory Structure

The repository uses a version-based architecture where plugins are organized by minimum j-lawyer.org version:
- `src/2.0.0.0/` - For j-lawyer 2.0+
- `src/2.1.0.0/` - For j-lawyer 2.1+ (added Gravity Forms support)
- `src/2.4.0.0/` - For j-lawyer 2.4+
- `src/2.6.0.0/` - For j-lawyer 2.6+ (introduced StorageLib changes for external document IDs)
- `src/3.4.0.0/` - For j-lawyer 3.4+ (added AI capabilities)

Each version directory contains plugins compatible with that version and later, unless breaking changes necessitate a new version.

### Plugin Structure

Every plugin consists of:
1. **`<pluginname>_meta.groovy`**: Metadata file defining name, description, version, author, and updated date
2. **`<pluginname>_ui.groovy`**: Main UI and logic implementation
3. **Additional supporting `.groovy` files**: Helper libraries specific to the plugin

### Core Library (formslib)

The `formslib` library provides shared functionality across all plugins:
- **FormsLib.groovy**: Core utilities and helper functions
- **StorageLib.groovy**: Case data persistence layer (breaking changes in 2.0, 2.6, and 3.4)
- **GuiLib.groovy**: GUI component utilities
- **TablePropertiesUtils.groovy**: Table property management
- **AttributeCellEditor.groovy**: Custom cell editor for tables

Plugins declare dependency on formslib via the `depends="formslib"` attribute in `j-lawyer-forms.xml`.

### Plugin Registry

`src/j-lawyer-forms.xml` is the central registry that defines all available plugins. Each `<form>` entry specifies:
- Plugin ID, type (plugin/library), name, description
- Version and compatible j-lawyer versions (in `for` attribute)
- URL for file location
- Required files list
- Optional settings with UI configuration

Breaking changes are documented as XML comments (e.g., "StorageLib has incompatible changes" for v2.0, "AI capabilities" for v3.4).

## Common Commands

### Build and Upload
```bash
ant upload
```
This uploads plugins to the j-lawyer.org server. Requires environment variables:
- `UPLOADUSER`: Upload credentials username
- `UPLOADPWD`: Upload credentials password
- `UPLOADFORMSMETAXML`: Destination for j-lawyer-forms.xml
- `UPLOADFORMSBASE`: Base path for form files

The build system uses Apache Ant with NetBeans project structure (`build.xml`, `nbproject/`).

### Local Development Workflow

For iterative plugin development without pushing to production:

1. Create local forms directory:
```bash
mkdir -p ~/.j-lawyer-client/forms-internal/
```

2. Create `~/.j-lawyer-client/forms-internal/j-lawyer-forms-internal.xml` with your plugin definition pointing to local files:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<forms>
    <form id="myplugin01" type="plugin" depends="formslib"
          name="My Plugin" description="..." placeholder="MYPLG"
          version="0.9.0" for="3.4.0.0"
          url="file:///home/username/.j-lawyer-client/forms-internal"
          files="myplugin01_meta.groovy,myplugin01_ui.groovy"/>
</forms>
```

3. Edit plugin files in the local directory
4. Use j-lawyer client settings dialog to reload plugins (no restart needed)
5. Reload the case to test changes
6. When complete, move files to repository and update `src/j-lawyer-forms.xml`

## Plugin Development Guidelines

### Creating a New Plugin

1. Choose the minimum j-lawyer version required
2. Create `<pluginname>_meta.groovy` in appropriate `src/<version>/` directory:
```groovy
name = "Plugin Name"
description = "Description of what it does"
version = "1.0.0"
author = "Your Name"
updated = "DD.MM.YYYY"
```

3. Create `<pluginname>_ui.groovy` with UI and logic implementation
4. Add plugin entry to `src/j-lawyer-forms.xml` with correct version mappings
5. Test locally using the local development workflow
6. Commit and push - GitHub Actions will automatically deploy

### Updating a Plugin

1. Update version in `<pluginname>_meta.groovy` in appropriate `src/<version>/` directory:
2. Update version in j-lawyer-forms.xml

### Version Compatibility

When creating plugins for multiple j-lawyer versions:
- If no breaking changes exist, point all versions to the same directory in `j-lawyer-forms.xml`
- If breaking changes exist (e.g., new AI capabilities in 3.4.0.0), create separate entries with different `url` attributes pointing to version-specific directories
- Common breaking change points: 2.0 (StorageLib calendar events), 2.6 (external document IDs), 3.4 (AI capabilities)

### Naming Conventions

- Plugin IDs: lowercase, descriptive (e.g., `verkehr01`, `miete01`, `arbeitsrecht02`)
- Placeholders: uppercase abbreviations (e.g., `VRKHR`, `MIETE`, `KSCHUTZ`)
- Meta files: `<pluginid>_meta.groovy`
- UI files: `<pluginid>_ui.groovy`
- Supporting libraries: PascalCase (e.g., `FormsLib.groovy`, `StorageLib.groovy`)

## AI-Enabled Plugins (j-lawyer 3.4.0.0+)

Starting with j-lawyer 3.4.0.0, plugins can support AI-powered data extraction from documents. AI-enabled plugins allow users to automatically populate form fields by extracting information from case documents using AI.

### Enabling AI Support in a Plugin

To make a plugin AI-enabled, three changes are required:

#### 1. Implement the FormAiMethods Interface

The plugin class must implement both `FormPluginMethods` and `FormAiMethods`:

```groovy
public class myplugin_ui implements com.jdimension.jlawyer.client.plugins.form.FormPluginMethods,
                                     com.jdimension.jlawyer.client.plugins.form.FormAiMethods {
    // ... plugin implementation
}
```

#### 2. Implement the setExtractedValues Method

This method is called by the j-lawyer client when AI-extracted values need to be populated into the form:

```groovy
public void setExtractedValues(Map<String,String> attributes) {
    // Use FormsLib to automatically populate UI components based on AiPromptKey
    FormsLib.setExtractedValues(attributes, this.SCRIPTPANEL);

    // Execute plugin-specific logic after values are set
    // Examples: calculations, validations, UI updates, field dependencies
    toggleSchadentyp();
    berechnen();
    updateDerivedFields();
    // ... additional plugin-specific methods
}
```

The `FormsLib.setExtractedValues()` helper method recursively traverses all UI components and automatically sets values for components that have the `AiPromptKey` client property matching keys in the attributes map.

Similarly, implement the getExtractionPrompt and isAiEnabled methods from interface FormAiMethods.

#### 3. Add AI Metadata to UI Components

Each form component that should receive AI-extracted values needs two additional client properties:

- **`clientPropertyAiPromptKey`**: Unique identifier key (e.g., `"unfall_datum"`, `"fahrzeug_kennzeichen"`)
- **`clientPropertyAiPromptDescription`**: Description for the AI explaining what information this field contains and any constraints

**Examples:**

```groovy
// TextField - simple text input
txtUnfallDatum = textField(
    id: 'sUnfallDatum',
    name: "_UNFALLDATUM",
    clientPropertyJlawyerdescription: "Unfalldatum",
    clientPropertyAiPromptKey: "unfall_datum",
    clientPropertyAiPromptDescription: "Unfalldatum",
    text: '',
    columns: 10
)

// TextField - with detailed description
txtKennzeichen = textField(
    id: 'sKennzeichen',
    clientPropertyJlawyerdescription: "Kennzeichen Mandant",
    clientPropertyAiPromptKey: "fahrzeug_kennzeichen",
    clientPropertyAiPromptDescription: "amtliches Kennzeichen des Fahrzeugs des Halters / Auftraggebers / Anspruchstellers oder amtliches Kennzeichen aus den Fahrzeugdaten",
    name: "_KENNZEICHEN",
    text: '',
    columns: 20
)

// ComboBox - include valid values in description
cmbFahrzeugart = comboBox(
    items: ['PKW', 'LKW', 'Motorrad', 'Fahrrad'],
    name: "_FHRZGART",
    clientPropertyJlawyerdescription: "Fahrzeugart",
    clientPropertyAiPromptKey: "fahrzeug_art",
    clientPropertyAiPromptDescription: "Art des Fahrzeugs des Halters / Auftraggebers / Anspruchstellers, zulässige Werte: PKW, LKW, Motorrad",
    editable: true
)

// CheckBox - specify valid values for boolean fields
chkPolizeiAufgenommen = checkBox(
    text: 'polizeilich aufgenommen',
    name: "_POLAUFGEN",
    clientPropertyJlawyerdescription: "polizeilich aufgenommen?",
    clientPropertyAiPromptKey: "unfall_polizei",
    clientPropertyAiPromptDescription: "Wurde der Unfall polizeilich aufgenommen? Zulässige Werte: ja, nein",
    selected: false
)

// TextArea - for longer text content
textArea(
    id: 'sUnfalllHergang',
    name: "_UNFALLHERGANG",
    clientPropertyJlawyerdescription: "Unfallhergang",
    clientPropertyAiPromptKey: "unfall_hergang",
    clientPropertyAiPromptDescription: "Unfallhergang oder Schadenhergang",
    lineWrap: true,
    wrapStyleWord: true,
    columns: 50,
    rows: 6
)

// Spinner - for numeric values
spnVorbesitzer = spinner(
    id: 'nVorbesitzer',
    clientPropertyJlawyerdescription: "Anzahl Vorbesitzer Mandant",
    clientPropertyAiPromptKey: "fahrzeug_vorbesitzer",
    clientPropertyAiPromptDescription: "Anzahl der Vorbesitzer des Fahrzeugs des Mandanten / Anspruchstellers",
    name: "_ANZVORBESITZER"
)
```

### AI Metadata Best Practices

- **Be specific**: Provide clear, unambiguous descriptions of what data the field expects
- **Include constraints**: For dropdowns and comboboxes, list all valid values in the description
- **Use consistent keys**: Use descriptive, consistent naming for `AiPromptKey` values (e.g., prefix related fields like `fahrzeug_*`, `unfall_*`)
- **Boolean fields**: Explicitly state valid values for checkboxes (e.g., "ja, nein" or "1, 0")
- **Context matters**: Include role context when relevant (e.g., "des Halters / Auftraggebers / Anspruchstellers" vs "des Unfallgegners")

### Supported Component Types

`FormsLib.setExtractedValues()` supports automatic value population for:
- `JTextField` - Sets text value
- `JTextArea` - Sets text value
- `JLabel` - Sets text value
- `JComboBox` - Selects matching item
- `JCheckBox` - Sets selected state (accepts: "1", "ja", "x" for true)
- `JSpinner` - Sets numeric value

### Post-Processing in setExtractedValues

After `FormsLib.setExtractedValues()` populates the fields, implement plugin-specific logic:

1. **Trigger calculations**: Recalculate derived fields, totals, or computed values
2. **Update dependent fields**: Populate fields that depend on AI-extracted values (e.g., nutzungsausfall rates)
3. **Toggle visibility**: Show/hide UI sections based on extracted data
4. **Validate data**: Check for consistency or missing required fields
5. **Format data**: Normalize dates, numbers, or text formatting

Example from verkehr01:
```groovy
public void setExtractedValues(Map<String,String> attributes) {
    FormsLib.setExtractedValues(attributes, this.SCRIPTPANEL);

    // Update visibility based on damage type
    toggleSchadentyp();
    togglePrivatGeschaeft();

    // Recalculate all cost fields with regulatory/difference columns
    berechnen(txtReparaturKosten, txtReparaturKostenMwst, txtReparaturKostenReg, txtReparaturKostenDiff);
    berechnen(txtWertminderung, null, txtWertminderungReg, txtWertminderungDiff);

    // Auto-populate dependent dropdown if extracted field is empty
    if(txtNutzungsausfallTagessatz.text == null || "".equals(txtNutzungsausfallTagessatz.text)) {
        populateNutzungsausfallTagessatz(cmbNutzAusfallGruppe.getSelectedItem(), cmbFahrzeugart.getSelectedItem().toString());
    }

    // Recalculate date-based fields
    berechnenNutzungsausfall(txtNutzungsAusfall, txtNutzungsAusfallReg, txtNutzungsAusfallDiff,
                             cmbNutzAusfallGruppe.getSelectedItem(), txtNutzungsAusfallVon,
                             txtNutzungsAusfallBis, cmbFahrzeugart.getSelectedItem().toString());

    // Final totals calculation
    berechnen();
    berechnenWba();
}
```

### Migrating Existing Plugins to AI

To add AI support to an existing plugin (for j-lawyer 3.4.0.0+):

1. Copy plugin files from `src/2.6.0.0/` to `src/3.4.0.0/`
2. Update class declaration to implement `FormAiMethods`
3. Add the `setExtractedValues()` method
4. Add `clientPropertyAiPromptKey` and `clientPropertyAiPromptDescription` to relevant UI components
5. Test locally with j-lawyer 3.4.0.0+ client
6. Add new `<form>` entry in `src/j-lawyer-forms.xml` for version 3.4.0.0 with comment "breaking changes introduced with 3.4: AI capabilities"

## Continuous Integration

GitHub Actions workflow (`.github/workflows/main.yml`) automatically deploys on push to master:
1. Sets up JDK 8
2. Downloads JSch libraries for SCP
3. Runs `ant upload` to deploy files to j-lawyer.org server

## License

All code is licensed under GNU AGPL v3. Each Groovy file includes the full license header.
