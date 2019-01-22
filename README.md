# ReCiter-Scopus-Retrieval-Tool

![Build Status](https://codebuild.us-east-1.amazonaws.com/badges?uuid=eyJlbmNyeXB0ZWREYXRhIjoid0xxSUlOU1ZkOW9QNkwyem5wbDJKQTNieEZHMXNCc3NXalJJRlVCR2c3KzJtWDByOWVOek54Ukh4TXVHcGhsYUFFajk2Y0tRNDVNUzF1SHJPVDhhaDM0PSIsIml2UGFyYW1ldGVyU3BlYyI6IjNCZEI0Q3RoN3hWME1uM3QiLCJtYXRlcmlhbFNldFNlcmlhbCI6MX0%3D&branch=master)
[![GitHub version](https://badge.fury.io/gh/wcmc-its%2FReCiter-Scopus-Retrieval-Tool.svg)](https://badge.fury.io/gh/wcmc-its%2FReCiter-Scopus-Retrieval-Tool)
[![codebeat badge](https://codebeat.co/badges/7a467876-39c4-4e2b-8987-0183f596ffd9)](https://codebeat.co/projects/github-com-wcmc-its-reciter-scopus-retrieval-tool-master)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)
[![Pending Pull-Requests](http://githubbadges.herokuapp.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/pulls.svg?style=flat)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/pulls)
[![Open Issues](http://githubbadges.herokuapp.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/issues.svg?style=flat)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/issues)
[![star this repo](http://githubbadges.com/star.svg?user=wcmc-its&repo=ReCiter-Scopus-Retrieval-Tool&style=flat)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool)
[![fork this repo](http://githubbadges.com/fork.svg?user=wcmc-its&repo=ReCiter-Scopus-Retrieval-Tool&style=flat)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/fork)
[![Github All Releases](https://img.shields.io/github/downloads/wcmc-its/ReCiter-Scopus-Retrieval-Tool/total.svg)]()
[![Open Source Love](https://badges.frapsoft.com/os/v3/open-source.svg?v=102)](https://github.com/wcmc-its/ReCiter-Scopus-Retrieval-Tool/) 

Authorized users may download records from the Scopus database. This application presents an easier to use interface for developers who use Scopus. It corrects some errors in Scopus including cases where it duplicates authors.

This application was written to work with [ReCiter](https://github.com/wcmc-its/ReCiter/), a tool for disambiguating articles written in PubMed and also indexed in Scopus. However, this application can work as a standalone service. In testing, it does help improve accuracy by several percentage points, but Scopus is not necessary to run ReCiter.


## Installing
To provide...


## Configuring

1. Get API key and insttoken from Elsevier. [This Elsevier document](https://dev.elsevier.com/tecdoc_api_authentication.html) describes how to obtain these codes.

2. Enter the API key into your Environment Variables where field name = `SCOPUS_API_KEY`.
- If you are deploying locally, go to terminal and write `export SCOPUS_API_KEY={api-key}`. 
- If you are deploying to an AWS instance, [add the environment variable](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/environments-cfg-softwaresettings.html#environments-cfg-softwaresettings-console) in the Elastic Beanstalk configuration section.

3. Enter the insttoken into your Environment Variables where field name = `SCOPUS_INST_TOKEN`.
- If you are deploying locally, go to terminal and write `export SCOPUS_INST_TOKEN={api-key}`. 
- If you are deploying to an AWS instance, [add the environment variable](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/environments-cfg-softwaresettings.html#environments-cfg-softwaresettings-console) in the Elastic Beanstalk configuration section.



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
