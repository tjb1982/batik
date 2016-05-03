(defproject batik "0.1.0-SNAPSHOT"
  :description "Titan + Blueprints GraphSail"
  :license {:name "LGPL"
            :url "http://www.gnu.org/licenses/lgpl-2.1.txt"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.thinkaurelius.titan/titan-all "0.5.4"]
                 [com.thinkaurelius.titan/titan-cassandra "0.5.4"
                  :exclusions [com.google.guava/guava]
                  ]
                 [com.google.guava/guava "15.0"]
                 [com.tinkerpop.blueprints/blueprints-sail-graph "2.6.0"]
                 [com.tinkerpop.blueprints/blueprints-graph-sail "2.6.0"]
                 ])
