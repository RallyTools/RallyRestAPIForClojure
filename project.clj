(defproject com.rallydev/clj-rally "0.9.0"
  :description "A clojure library for interating with Rally's webservice API."
  :url "https://github.com/RallyTools/RallyRestAPIForClojure"
  :license {:name "MIT License"
            :url "http://en.wikipedia.org/wiki/MIT_License"}
  :dependencies [[camel-snake-kebab "0.3.2" :exclusions [org.clojure/clojure]]

                 [cheshire "5.5.0"]

                 [clj-http "2.0.0"]
                 [clj-time "0.11.0" :exclusions [org.clojure/clojure]]

                 [environ "1.0.1"]

                 [slingshot "0.12.2"]]
  :jvm-opts ["-Xmx1g"]
  :test-selectors {:default     (complement :integration)
                   :integration :integration}
  :plugins [[lein-pprint "1.1.1"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[criterium "0.4.3"]
                                  [org.clojure/clojure "1.8.0-RC2"]
                                  [crypto-random "1.2.0"]]}})
 
