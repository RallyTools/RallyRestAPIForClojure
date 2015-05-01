# RallyRestAPIForClojure

RallyRestAPIForClojure is a Clojure library to access your Rally data. It currently supports querying, creates, reads, updates and deletes. If you would like more inforamtion on the Rally Rest API please see Rally's [Web Services API documentation](https://rally1.rallydev.com/slm/doc/webservice).

[![Build Status](https://travis-ci.org/RallyTools/RallyRestAPIForClojure.svg?branch=master)](https://travis-ci.org/RallyTools/RallyRestAPIForClojure)

### Creating a REST API
The rest API is the central object of the Clojure API for Rally. An API can be created with a username/password
or with an API key.

```clojure
(require '[rally-api.api :as api])

(def rest-api (api/create-rest-api {:username "me@mycompany.com" :password "supersecret"}))
; => #'user/rest-api

(def rest-api (api/create-rest-api {:api-key "mysecret key"}))
; => #'user/rest-api
```

### Querying
The query API is written in a way that should be comfortable to most Clojure developers. There are three major parts to the query API.

1. [Rally Keyword -> Clojure Keyword Translation](#rally-keyword---clojure-keyword-tranlation)
2. [Query Specs](#query-specs)
3. [URI Generation](#uri-generation)

Before we get into the details, let's take a look at a simple example.

```clojure
(first (api/query rest-api :defect)
; => {:metadata/rally-api-major 2,
;     :metadata/rally-api-minor 0,
;     :metadata/ref  #<URI http://testing.rallydev.com/slm/webservice/v2.0/defect/12345,
;     :metadata/ref-object-uuid #uuid "f5f770f5-d1e9-4dc7-847a-fd9164d93127",
;     :metadata/ref-object-name "stuff is broken",
;     :metadata/type :defect}
```

This simple query gets translated into the URL `http://rally1.rallydev.com/slm/webservice/v2.0/Defect`.

A couple of things to notice about this first example:
* The keyword `:defect` is translated into `"Defect"`
* The results of the query are returned as a sequence of maps.
* The keys of each of the maps have been translated into clojure idiomatic keywords.
* api/query returns a lazy seq of all the paged results.

#### Rally Keyword -> Clojure Keyword Tranlation
The rest API tries to make working with Rally in Clojure seem natrual. Most of the translations Rally->Clojure and Clojure->Rally are done with a library called [camel-snake-kebab](https://github.com/qerub/camel-snake-kebab).
When going from Rally -> Clojure we use the [->kebab-case-keyword](https://github.com/qerub/camel-snake-kebab/blob/stable/src/camel_snake_kebab/core.cljx#L20) translation.

```clojure
(data/->clojure-case "Defect")
; => :defect

(data/->clojure-case "CurrentProjectName")
; => :current-project-name
```

The Rally->Clojure translation does a little more than just change case. There are three types of data that are returned in a Rally object: built in, custom and metadata fields.
The translation code handles each of these types of data a little differently.

In Rally, metadata fields start with an `_` (underscore). Custom fields start with a `c_`. The API translates each of these two data types into keywords with namespace that
represent their meaning.

```clojure
(data/->clojure-case "_ref")
; => :metadata/ref

(data/->clojure-case "c_MyCustomField")
; => :custom/my-custom-field
```

As you might guess, the API has the reverse function.

```clojure
(data/->rally-case :defect)
; => "Defect"
(data/->rally-case :current-project-name)
; => "CurrentProjectName"
(data/->rally-case :metadata/ref)
; => "_ref"
(data/->rally-case :custom/my-custom-field)
; => "c_MyCustomField"
```

These translations are used when writing queries and returning results. All object fields are translated using the `data/->clojure-case` function before return the results to the caller. The `data/->rally-case` function is used when translating queries to their proper Rally format.

#### Query Specs
Query specs are modeled after [honey-sql](https://github.com/jkk/honeysql). Let's look at a couple of examples to get the hang of it.

```clojure
(api/query rest-api :defect [:contains :name "Foo"])
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect?query=(Name contains "Foo")

(api/query rest-api :defect [:or [:= :name "Foo"] [:= :name "Junk"]])
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect?query=((Name = "Foo") OR (Name = "Junk"))

(api/query rest-api :defect [:and [:contains :name "Foo"]
                                  [:or [:= :state "Open"]
                                       [:= :state "In-Progress"]]])
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect?query=((Name contains "Foo") AND ((State = "Open") OR (State = "In-Progress")))

(api/query rest-api :userstory [:= :parent.name "Foo"])
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/HierarchicalRequirement?query=(Parent.Name = "Foo")
```

Query specs can also contain information like `pagesize` or `start`.

```clojure
(api/query rest-api :defect {:query [:= :name "Foo"] :pagesize 10})
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect?query=(Name = "Foo")&pagesize=10
```

#### URI Generation
Almost anything reasonable can be used as an URI in the API.

```clojure
(api/query rest-api :defect)
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect

(api/query rest-api :userstory)
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/HierarchicalRequirement

(api/query rest-api "http://rally1.rallydev.com/slm/webservice/v2.0/defect")
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect

(def my-defect (api/create! rest-api :defect {:name "foo"}))
(api/query rest-api (:tasks my-defect))
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect/123/tasks

(def my-defect (api/create! rest-api :defect {:name "foo"}))
(api/query rest-api [my-defect :tasks])
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect/123/tasks

(def my-defect (api/create! rest-api :defect {:name "foo"}))
(api/find rest-api my-defect)
;; Translates to http://rally1.rallydev.com/slm/webservice/v2.0/defect/123
```

### Creating Data
```clojure
;; Create a user story
(api/create! rest-api :user-story {:name "This feature is really cool"})
; => {:description "",
;     :formatted-id "US1",
;     :tags {:metadata/rally-api-major 2,
;            :metadata/rally-api-minor 0,
;            :metadata/ref  #<URI http://testing.rallydev.com/HierarchicalRequirement/1234/Tags,
;            :metadata/type :tag,
;            :metadata/tags-name-array [],
;            :count 0},
;     .... }
```
The API allows "defaulting" of data during `api/create!`. If you want to default data, then you will need to provide `default-data-fn`. The `default-data-fn` is a function
that takes 2 parameters. The parameters are a type and a data map. The type is the data type in which the user is trying to create (`:defect`, `:task`, ...) The data map is the map of data that will be used to create the object.
```clojure
(let [default-name (fn [type data] (merge {:name "Foo"} data))]
  (-> rest-api
      (request/set-default-data-fn default-name)
      (api/create! rest-api :userstory)))
```

### Updating Data
```clojure
(def my-user-story (api/create! rest-api :user-story {:name "This feature is really cool"}))
(api/update! rest-api my-user-story {:description "This is my description"})
```

#### Updating Collections
```clojure
;; Add a defect to a user story
(def my-user-story (api/create! rest-api :user-story {:name "This feature is really cool"}))
(def my-defect (api/create! rest-api :defect {:name "This doesn't work correctly"}))
(api/update-collection! rest-api (:defects my-user-story) :add [my-defect])
```

#### Setting Relationships
```clojure
;; Add a parent to a userstory
(def my-user-story (api/create! rest-api :user-story {:name "This feature is really cool"}))
(def my-parent (api/create! rest-api :user-story {:name "Really cool parent"}))
(api/update! rest-api my-user-story {:parent my-parent})
```

## License

Copyright (c) Rally Software Development Corp. 2013-2015 Distributed under the MIT License.

## Warranty

The Rally REST API for Clojure is available on an as-is basis. 

## Support

Rally Software does not actively maintain or support this toolkit.  If you have a question or problem, we recommend posting it to Stack Overflow: http://stackoverflow.com/questions/ask?tags=rally

