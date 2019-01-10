# freebyJenkinsLibrary

The Jenkins global library for use by freebyTech jenkins servers.

For more information on shared libaries see:

https://jenkins.io/doc/book/pipeline/shared-libraries/

## Custom Steps

The following custom steps are defined by this shared library:

| Step             | Usage                                                                    |
| ---------------- | ------------------------------------------------------------------------ |
| build            | Build the docker image, the DockerFile is expected to exist              |
|                  | in the ```/src``` directory as that is where it will attempt to find it. |
| getApproval      | Get approval for a deployment operation to the local kubernetes cluster. |
| deploy           | Deploy to the local kubernetes instance.                                 |

## Required Environment Variables

This shared libarary uses and expects and the following jenkins enviornment variables to exist before
build steps are used. These can be defined locally and pipeline specific, or defined globally in the Jenkins conifguration and used for all builds.

| Envrionment Variable               | Usage                                                     |
| ---------------------------------- | --------------------------------------------------------- |
| BUILD_NUMBER                       | The build number as defined by Jenkins, this is an        |
|                                    | environment variable automatically created by Jenkins.    |
| AGENT_IMAGE                        | The docker image to use for the build agent of the        |
|                                    | operation, needed when ```build``` or ```deploy``` steps  |
|                                    | are used.                                                 | 
| REGISTRY_URL                       | The harbor or docker registry used to push images to      |
|                                    | whenever docker builds are meant to be pushed or pulled.  |
|                                    | During a ```build``` step, if *helmBuildChart* is true in |
|                                    | a build operation this will also be the URL for the       |
|                                    | Harbor Chart Museum.      
|                                    | Note: Set to 'index.docker.io' when you wish to use the   |
|                                    | standard docker hub and won't be building helm charts.    |
| REGISTRY_USER_ID                   | The ID (in GUID format) of the user meant to login to     |
|                                    | to the docker registry, if pushing Helm charts this user  |
|                                    | must also exist in the Harbor Chart Museum.               |
| NUGET_API_KEY                      | During a ```build``` step, if *nugetPushOption* is set    |
|                                    | to push a nuget package this is the API key it will use   |
|                                    | to perform the operation.                                 |

## Repository Assumptions

In order to use the steps defined in this shared library, the following repository layout is assumed:

### Source Repository Structure

No matter what the type of .NET Core application, the source code and the DockerFile exist in this structure:
```
/src
    \_ DockerFile
```

If supporting Helm Charts, the Helm Chart exists in this structure:
```
/deploy/[imageName]
```
Where the Helm Chart will be published and pulled under ```imageName```.

### CSProj File Settings
In order to properly support build versioning and also nuget package pushes the following settings should be filled out appropriately in the csproj file in a ```PropertyGroup```.

```xml
  <PropertyGroup>
    <Product>[product name]</Product>
    <Authors>[author]</Authors>
    <Company>[company name]</Company>
    <Copyright>Copyright © 2019</Copyright>

    <!-- FileVersion could also be set seperately, 
    otherwise this is used by for product version 
    and also for nuget version as well. -->
    <Version Condition=" '$(BUILD_VERSION)' == '' ">0.1.0.0</Version>
    <Version Condition=" '$(BUILD_VERSION)' != '' ">$(BUILD_VERSION)</Version>

    <!-- NuGet Package ID -->
    <PackageId Condition=" '$(PACKAGE_ID)' == '' ">[some default name]</PackageId>
    <PackageId Condition=" '$(PACKAGE_ID)' != '' ">$(PACKAGE_ID)</PackageId>
  </PropertyGroup>
```

```BUILD_VERSION``` and ```PACKAGE_ID``` will be defined during the docker build operation. ```PACKAGE_ID``` is only used in NuGet package pack and publish operations.

### .NET Core Class Library Build Output

If the class library build will be published to NuGet, then the following is assumed about the build output:

/lib/nuget - If ```nugetPushOption``` is ```NugetPushOptionEnum.PushRelease```, the release version will be built to here within the image.

/lib/nuget_d - If ```nugetPushOption``` is ```NugetPushOptionEnum.PushDebugAndSymbols```, the Debug version will be built to here within the image along with the symbols package.

