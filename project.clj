(defproject com.rallydev/clj-rally "0.12.0"
  :description "A clojure library for interating with Rally's webservice API."
  :url "https://github.com/RallyTools/RallyRestAPIForClojure"
  :license {:name "MIT License"
            :url  "http://en.wikipedia.org/wiki/MIT_License"}
  :deploy-repositories {"clojars" {:sign-releases false}}
  :dependencies [[camel-snake-kebab "0.4.0"]
                 [cheshire "5.6.3"]
                 [clj-http "3.3.0"]
                 [clj-time "0.12.0"]
                 [environ "1.1.0"]
                 [slingshot "0.12.2"]]
  :test-selectors {:default     (complement :integration)
                   :integration :integration}
  :plugins [[lein-pprint "1.1.1"]]
  :profiles {:dev {:jvm-opts     ["-Xmx1g"]
                   :source-paths ["dev"]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [criterium "0.4.4"]
                                  [crypto-random "1.2.0"]]}})
