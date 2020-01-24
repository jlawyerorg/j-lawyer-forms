[![Build Status](https://api.travis-ci.org/jlawyerorg/j-lawyer-forms.svg?branch=master)](https://travis-ci.org/jlawyerorg/j-lawyer-forms)

# j-lawyer-forms
j-lawyer.org plugin repository. Form plugins can be rolled out without updating the j-lawyer.org client or server. 

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

## Documentation

Groovy scripting: http://groovy-lang.org
