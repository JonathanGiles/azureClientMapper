# Azure Client Mapper

This tool is used for mapping async APIs that exist in the current generation of Azure SDK libraries to the new generation of 
sync-only Azure SDK libraries. The output from this tool can be used as part of OpenRewrite recipes to help map user
code from these async APIs to the new sync APIs.

## Usage

To use this tool, you need to have a config.json file in the root of the repository. This file contains the mapping
information for the tool. The format of this file is as follows:

```json
[
  {
    "oldLibrary": "com.azure:azure-storage-blob:12.29.0",
    "newLibrary": "",
    "clientMappings": {
        "com.azure.storage.blob.BlobServiceAsyncClient": "com.azure.v2.storage.blob.BlobServiceClient",
        ... more client mappings ...
    }
  },
  ... more mappings ...
]
```

In the above JSON, the following properties are used:

- `oldLibrary`: The Maven coordinates of the old library that contains the async client that you want to map.
- `newLibrary`: The Maven coordinates of the new library that contains the sync client that you want to map to. This can be left as an empty string if the new library is not yet released.
- `clientMappings`: A map of fully-qualified class names of the async clients to the fully-qualified class names of the sync clients. This is used to guide the tool, otherwise it will try its best to find the best match.

You can execute the tool by running the `public static void main` method in the `Main` class.

## Output

Once the tool runs, there will be a `mappings` directory in the root of the repository. This directory will contain
a file for each library that was mapped. The file will contain a JSON object that resembles the following:

```json
{
  "oldLibrary" : "com.azure:azure-storage-blob:12.29.0",
  "clients" : {
    "com.azure.storage.blob.BlobAsyncClient" : {
      "methods" : {
        "getAppendBlobAsyncClient" : {
          "parameters" : [ ],
          "matches" : [ ]
        },
        "getBlockBlobAsyncClient" : {
          "parameters" : [ ],
          "matches" : [ ]
        },
        "getBlockID" : {
          "parameters" : [ ],
          "matches" : [ ]
        },
        "getCustomerProvidedKeyAsyncClient" : {
          "parameters" : [ {
            "type" : "CustomerProvidedKey",
            "name" : "customerProvidedKey"
          } ],
          "matches" : [ ]
        }
      }
    },
    "com.azure.storage.blob.BlobContainerAsyncClient" : {
      "methods" : {
        "create": {
          "parameters": [],
          "matches": []
        },
        "createIfNotExists": {
          "parameters": [],
          "matches": []
        },
        "createIfNotExistsWithResponse": {
          "parameters": [
            {
              "type": "BlobContainerCreateOptions",
              "name": "options"
            },
            {
              "type": "Context",
              "name": "context"
            }
          ],
          "matches": []
        }
      }
    }
  }
}
```

The output mapping file contains a JSON object with the following properties:

- `oldLibrary`: The Maven coordinates of the old library that contains the async client that you want to map.
- `clients`: A map of fully-qualified class names of the async clients to the fully-qualified class names of the sync clients. This is used to guide the tool, otherwise it will try its best to find the best match.
  - `methods`: A map of method names to information about the method.
    - `parameters`: A list of parameter types and names for the method.
    - `matches`: A list of matches for the method. Each match is a map of fully-qualified class names to method names. The key is the fully-qualified class name of the sync client and the value is the method name that is a match.