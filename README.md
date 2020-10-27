[![Build Status](https://api.travis-ci.org/jlawyerorg/j-lawyer-forms.svg?branch=master)](https://travis-ci.org/jlawyerorg/j-lawyer-forms)

# j-lawyer-forms
This is the j-lawyer.org plugin repository. Form plugins can be rolled out without updating the j-lawyer.org client or server. 

## How to write a plugin

* Create a file called `<pluginname>_meta.groovy` in directory `src/<j-lawyer-org-version>`, where j-lawyer-org-version is the minimum j-lawyer.org version required for the plugin to run. The file is to describe the plugin in general, e.g. its name and description:
```
name = "Verkehrsunfalldaten"
description = "Falldatenblatt fuer Verkehrsunfallsachen"
version = "1.0.0";
author = "Jens Kutschke"
updated = "25.01.2020"
```
* Create a file called `<pluginname>_ui.groovy` in directory `src/<j-lawyer-org-version>`, where j-lawyer-org-version is the minimum j-lawyer.org version required for the plugin to run. The file will contain both the user interface and logic for your plugin. It might be comprised by additional groovy script files. See some of the existing plugins for how to develope a plugin
* Edit file `j-lawyer-forms.xml` and add your plugin. 
You need one entry per j-lawyer.org version, for each of the versions that you would like to provide the plugin for. Attributes: name = Name of the plugin; version = Version of the plugin; for = for which j-lawyer.org version; url = directory where the files are kept. If there are no different versions of the plugin for different j-lawyer.org versions, you can just point to the same / ONE directory. files = list of files that are required for this plugin to run.

* Once you commit and push the changes, the plugin will automatically be uploaded so that all users can use it.

## Local forms plugin development

Developing a new form is an iterative process. There is a way to first develop plugins locally before pushing changes to the GitHub repository. This will also avoid your changes to be overwritten during client restarts.

* Create a directory /home/<username>/.j-lawyer-client/forms-internal/
* Put a file j-lawyer-forms-internal.xml into this directory
* Edit the file and put in the plugin that you are working on, e.g. like below. Note that the url points to a local directory.
```
<?xml version="1.0" encoding="UTF-8"?>
<forms>
    <form id="verkehr01int" type="plugin" depends="formslib" name="Verkehrsunfalldaten-int" description="Falldatenblatt zur Erfassung von Verkehrsunfalldaten" placeholder="VRKHRINT" version="0.9.0" for="1.12.0.1,1.12.0.2" url="file:///home/<username>/.j-lawyer-client/forms-internal" files="verkehr01int_meta.groovy,verkehr01int_ui.groovy"/>
</forms>
```
* Edit your form plugins files in the local directory. When changed, use the settings dialog to update them. There is no need to restart the client, just reload the case ("Akte").
* Once done, you may upload the files to the official repository. Remove any "internal" names.

## Documentation

Groovy scripting: http://groovy-lang.org
