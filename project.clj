(defproject com.rallydev/clj-rally "0.12.2"
  :description "A clojure library for interating with Rally's webservice API."
  :url "https://github.com/RallyTools/RallyRestAPIForClojure"
  :license {:name "MIT License"
            :url  "http://en.wikipedia.org/wiki/MIT_License"}
  :deploy-repositories {"clojars" {:sign-releases false}}
  :dependencies [[camel-snake-kebab "0.4.3"]
                 [cheshire "5.12.0"]
                 [clj-http "3.12.3"]
                 [clj-time "0.15.2"]
                 [environ "1.2.0"]
                 [slingshot "0.12.2"]]
  :test-selectors {:default     (complement :integration)
                   :integration :integration}
  :plugins [[lein-ancient "1.0.0-RC3"]
            [lein-pprint "1.3.2"]]
  :profiles {:dev {:jvm-opts     ["-Xmx1g"]
                   :source-paths ["dev"]
                   :dependencies [[org.clojure/clojure "1.11.2"]
                                  [criterium "0.4.6"]
                                  [crypto-random "1.2.1"]]}})
