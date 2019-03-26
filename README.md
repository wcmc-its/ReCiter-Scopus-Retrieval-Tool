# ReCiter-Scopus-Retrieval-Tool

![Build Status](https://codebuild.us-east-1.amazonaws.com/badges?uuid=eyJlbmNyeXB0ZWREYXRhIjoid0xxSUlOU1ZkOW9QNkwyem5wbDJKQTNieEZHMXNCc3NXalJJRlVCR2c3KzJtWDByOWVOek54Ukh4TXVHcGhsYUFFajk2Y0tRNDVNUzF1SHJPVDhhaDM0PSIsIml2UGFyYW1ldGVyU3BlYyI6IjNCZEI0Q3RoN3hWME1uM3QiLCJtYXRlcmlhbFNldFNlcmlhbCI6MX0%3D&branch=master)
![version](https://img.shields.io/badge/version-1.0-blue.svg?maxAge=2592000)
[![codebeat badge](https://codebeat.co/badges/7a467876-39c4-4e2b-8987-0183f596ffd9)](https://codebeat.co/projects/github-com-wcmc-its-reciter-scopus-retrieval-tool-master)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)
[![Pending Pull-Requests](https://img.shields.io/github/issues-pr-raw/wcmc-its/ReCiter-Scopus-Retrieval-Tool.svg?color=blue)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/pulls?q=is%3Aopen+is%3Apr)
[![Closed Pull-Requests](https://img.shields.io/github/issues-pr-closed-raw/wcmc-its/ReCiter-Scopus-Retrieval-Tool.svg?color=blue)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/pulls?q=is%3Apr+is%3Aclosed)
[![GitHub issues open](https://img.shields.io/github/issues-raw/wcmc-its/ReCiter-Scopus-Retrieval-Tool.svg?maxAge=2592000)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/issues?q=is%3Aopen+is%3Aissue)
[![GitHub issues closed](https://img.shields.io/github/issues-closed-raw/wcmc-its/ReCiter-Scopus-Retrieval-Tool.svg?maxAge=2592000)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/issues?q=is%3Aissue+is%3Aclosed)
[![star this repo](http://githubbadges.com/star.svg?user=wcmc-its&repo=ReCiter-Scopus-Retrieval-Tool&style=flat)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool)
[![fork this repo](http://githubbadges.com/fork.svg?user=wcmc-its&repo=ReCiter-Scopus-Retrieval-Tool&style=flat)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/fork)
[![Tags](https://img.shields.io/github/tag/wcmc-its/ReCiter-Scopus-Retrieval-Tool.svg?style=social)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/releases)
[![Github All Releases](https://img.shields.io/github/downloads/wcmc-its/ReCiter-Scopus-Retrieval-Tool/total.svg)]()
[![Open Source Love](https://badges.frapsoft.com/os/v3/open-source.svg?v=102)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/) 

Authorized users may download records from the Scopus database. This application presents an easier to use interface for developers who use Scopus. It corrects some errors in Scopus including cases where it duplicates authors.

This application was written to work with [ReCiter](https://github.com/wcmc-its/ReCiter/), a tool for disambiguating articles written in PubMed and also indexed in Scopus. However, this application can work as a standalone service. In testing, it does help improve accuracy by several percentage points, but Scopus is not necessary to run ReCiter.


## Advantages over using Scopus API alone

This tool has several advantages over using the Scopus API alone:
- The Scopus API outputs data as XML while the ReCiter Scopus Retrieval Tool outputs data as JSON, a format which is easier for developers to use.
- The Scopus API sometimes creates redundant author objects or otherwise doesn’t properly assign sequence numbers to authors. This tool addresses both problems.



## Installing

1. Navigate to directory where you wish to install the application, e.g., `cd ~/Paul/Documents/`
2. Clone the repository: `git clone https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool.git`
3. Navigate to the newly installed folder: `cd ReCiter-Scopus-Retrieval-Tool`
4. Use Maven to build the project: `mvn clean install -Dmaven.test.skip=true`
5. Set the SCOPUS_API_KEY key and SCOPUS_INST_TOKEN. (See "Obtaining an API key and INST_TOKEN" below)
- Option #1: Command line
  - Enter `export SCOPUS_API_KEY=[enter your API key here]`
  - Enter `export SCOPUS_INST_TOKEN=[enter your INST_TOKEN here]`
- Option #2: Enter as an environment variable in AWS itself. If you are deploying to an AWS instance, [add the environment variable](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/environments-cfg-softwaresettings.html#environments-cfg-softwaresettings-console) in the Elastic Beanstalk configuration section.
- Option #3: In Eclipse application
  - Open Eclipse
  - Right-click on Application.java found here: ReCiter-Scopus-Retrieval-Tool --> src/main/java --> reciter --> Application.java
  - Click on "Run As..." --> "Run Configurations..."
  - Click on "ReCiter-Scopus-Retrieval-Tool" in sidebar
  - Click on "Environment" tab
  - Under variable, add "SCOPUS_API_KEY" and enter the API key.
  - Under variable, add "SCOPUS_INST_TOKEN" and enter the INST_TOKEN.
6. Set the desired port
- Option #1: Set at the system level using this command `export SERVER_PORT=[your port number]`. This supersedes any ports set in application.properties.
- Option #2: Update the application.properties file located at `/src/main/resources/` Make sure the port doesn't conflict with other services such as ReCiter or ReCiter PubMed Retrieval Tool.
7. Build Maven instance `mvn spring-boot:run`
8. Visit `http://localhost:[your port number]/swagger-ui.html` to see the Swagger page for this service.


## Obtaining an API key and INST_TOKEN

[This Elsevier document](https://dev.elsevier.com/tecdoc_api_authentication.html) describes how to obtain an API key and INST_TOKEN from Elsevier. 




## Using

Ths Scopus Retrieval Tool API only allows you to search one field at a time. Field codes are listed on the [Elsevier site](https://service.elsevier.com/app/answers/detail/a_id/11236/supporthub/scopus/#tips) or in the [Scopus data model](https://github.com/wcmc-its/ReCiter-Scopus-Model) for this tool.



### Search for one PMID
```
{
  "query": [
    "20000000"
  ],
  "type": "PMID"
}
```

![https://raw.githubusercontent.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/master/files/SearchScopus-PMID.gif](https://raw.githubusercontent.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/master/files/SearchScopus-PMID.gif)


### Search for multiple PMIDs
```
{
  "query": [
      "20000000",
      "21000000"
  ],
  "type": "PMID"
}
```

### Search by DOI
```
{
  "query": [
     "10.1155/2018/1920276"
  ],
  "type": "doi"
}
```

![https://raw.githubusercontent.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/master/files/SearchScopus-DOI.gif](https://raw.githubusercontent.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/master/files/SearchScopus-DOI.gif)

### Search by Scopus Doc ID
```
{
  "query": [
     "34547687746"
  ],
  "type": "scopus-id"
}
```

### Search by institutional affiliation identifier
```
{
  "query": [
    "60007997"
  ],
  "type": "af-id"
}
```
