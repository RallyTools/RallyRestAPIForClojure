# RallyRestAPIForClojure

RallyRestAPIForClojure is a Clojure library to access your Rally data. It currently supports querying, creates, reads, updates and deletes. If you would like more inforamtion on the Rally Rest API please see Rally's [Web Services API documentation](https://rally1.rallydev.com/slm/doc/webservice).

## Examples

```clojure
(require '[rally-api.api :as api])

(def rest-api (api/create-rest-api {:username "me@mycompany.com" :password "supersecret"}))
; => #'user/rest-api

;; Query for all the defects in your default project.
;; 
;; Notice that results have been returned in a idiomatic clojure format.
;; Rally returns several pieces of metadata for each object returned. In the regular Rally rest results, metadata is denoted by an
;; underscore. The api translates _ref to :metadata/ref. All pieces of metadata are translated in much the same way.
;;
;; api/query returns a lazy sequence that takes care of paging through all the results of your query.
(first (api/query rest-api :defect)
; => {:metadata/rally-api-major 2,
      :metadata/rally-api-minor 0,
      :metadata/ref  #<URI http://testing.rallydev.com/slm/webservice/v2.0/defect/12345,
      :metadata/ref-object-uuid #uuid "f5f770f5-d1e9-4dc7-847a-fd9164d93127",
      :metadata/ref-object-name "stuff is broken",
      :metadata/type :defect}

;; Create a user story
(api/create! rest-api :user-story {:name "This feature is really cool"})
; => {:description "",
      :formatted-id "US1",
      :tags {:metadata/rally-api-major 2,
             :metadata/rally-api-minor 0,
             :metadata/ref  #<URI http://testing.rallydev.com/HierarchicalRequirement/1234/Tags,
             :metadata/type :tag,
             :metadata/tags-name-array [],
             :count 0},
      .... }
      
;; Query for all user stories whose name contains cool or exciting
(api/query rest-api :user-story {:query [:or [:contains :name "cool"] [:contains :name "exciting"]]})
; => ({:metadata/rally-api-major 2,
       :metadata/rally-api-minor 0,
       :metadata/ref  #<URI http://localhost:7001/slm/webservice/v2.0/hierarchicalrequirement/503114>,
       :metadata/ref-object-uuid  #uuid "7e643822-f751-455a-9821-0a5fafe46d3a",
       :metadata/ref-object-name "This feature is really cool",
       :metadata/type :user-story})
```

## License

Copyright (c) Rally Software Development Corp. 2013-2015 Distributed under the MIT License.

## Warranty

The Rally REST API for Clojure is available on an as-is basis. 

## Support

Rally Software does not actively maintain or support this toolkit.  If you have a question or problem, we recommend posting it to Stack Overflow: http://stackoverflow.com/questions/ask?tags=rally


