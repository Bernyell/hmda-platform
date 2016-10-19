# HMDA Platform API

## Public HTTP Endpoints

* `/`
    * `GET` - Root endpoint, with information about the HMDA Platform service. Used for health checks

    Example response, with HTTP code 200:

    ```json
    {
      "status": "OK",
      "service": "hmda-api",
      "time": "2016-06-17T13:54:10.725Z",
      "host": "localhost"
    }
    ```

All endpoints in the `/institutions` namespace require two headers (see "Authorization" section below for more detail):
* `CFPB-HMDA-Username`, containing a string
* `CFPB-HMDA-Institutions`, containing a list of integers


* `/institutions`
    * `GET` - List of Financial Institutions

    Example response, with HTTP code 200:

    ```json
    {
      "institutions": [
        {
          "id": "12345",
          "name": "First Bank",
          "status": "active"
        },
        {
          "id": "123456",
          "name": "Second Bank",
          "status": "inactive"
        }
      ]
    }
    ```

* `/institutions/<institution>`
    * `GET` - Details for Financial Institution

    Example response, with HTTP code 200:

    ```json
    {
      "institution": {
      "id": "12345",
      "name": "First Bank",
      "status": "active"
    },
      "filings": [
        {
          "period": "2017",
          "institutionId": "12345",
          "status": {
            "code": 1,
            "message": "not-started"
          }
        },
        {
          "period": "2016",
          "institutionId": "12345",
          "status": {
            "code": 3,
            "message": "completed"
          }
        }
      ]
    }
    ```


* `/institutions/<institution>/filings/<period>`
  * `GET` - Details for a filing

  Example response, with HTTP code 200:

  ```json
  {
    "filing": {
      "period": "2017",
      "institutionId": "12345",
      "status": {
        "code": 1,
        "message": "not-started"
      }
    },
    "submissions": [
      {
        "id": {
          "institutionId": "12345",
          "period": "2017",
          "sequenceNumber": 1
        },
        "status": {
          "code": 1,
          "message": "created"
        }
      },
      {
        "id": {
          "institutionId": "12345",
          "period": "2017",
          "sequenceNumber": 2
        },
        "status": {
          "code": 1,
          "message": "created"
        }
      },
      {
        "id": {
          "institutionId": "12345",
          "period": "2017",
          "sequenceNumber": 3
        },
        "status": {
          "code": 1,
          "message": "created"
        }
      }
     ]
  }
  ```

* `/institutions/<institution>/filings/<period>/submissions`

    * `POST` - Create a new submission

    Example response, with HTTP code 201:

    ```json
    {
      "id": {
        "institutionId": "0",
        "period": "2016",
        "sequenceNumber": 1
      },
      "status": {
        "code": 1,
        "message": "created"
      }
    }
    ```

* `/institutions/<institution>/filings/<period>/submissions/latest`

    * `GET` - The latest submission for some institution and period

     Example response, with HTTP code 200:

    ```json
    {
      "id": 3,
      "status": {
        "code": 1,
        "message": "created"
      }
    }
    ```


* `/institutions/<institution>/filings/<period>/submissions/<submissionId>`
    * `POST` - Upload HMDA data to submission


* `/institutions/<institution>/filings/<period>/submissions/<submissionId>/edits`
    * `GET`  - List of all edits for a given submission, grouped by edit type

    Example response, with HTTP code 200:

    ```json
    {
      "syntactical": {
        "edits": [
          {
            "edit": "S025",
            "ts": true,
            "lars": [
              {
                "lar": {"loanId": "s1"}
              },
              {
                "lar": {"loanId": "s2"}
              },
              {
                "lar": {"loanId": "s3"}
              }
            ]
          },
          {
            "edit": "S010",
            "ts": false,
            "lars": [
              {
                "lar": {"loanId": "s4"}
              },
              {
                "lar": {"loanId": "s5"}
              }
            ]
          }
        ]
      },
      "validity": {},
      "quality": {},
      "macro": {}
    }
    ```

* `/institutions/<institution>/filings/<period>/submissions/<submissionId>/edits/<syntactical|validity|quality|macro>`
    * `GET`  - List of edits of a specific type, for a given submission

    Example response, with HTTP code 200:

```json
{
  "edits": [
    {
      "edit": "V555",
      "ts": false,
      "lars": [
        {
          "lar": {
            "loanId": "4977566612"
          }
        }
      ]
    },
    {
      "edit": "V550",
      "ts": false,
      "lars": [
        {
          "lar": {
            "loanId": "4977566612"
          }
        }
      ]
    }
  ]
}
```

## Authorization
Each endpoint that starts with `/institutions` is protected by three authorization requirements.

* Requests must include the `CFPB-HMDA-Username` header.
  * Its value should be the username of the user making the request.
* Requests must include the `CFPB-HMDA-Institutions` header.
  * This header will contain the comma-separated list of institution IDs
    that the user is authorized to view.
* For requests to institution-specific paths, such as `/institutions/<institution>`
  and `/institutions/<institution>/summary` (any endpoint except `/institutions`),
  the institution ID requested must match one of the IDs in the `CFPB-HMDA-Institutions`
  header.