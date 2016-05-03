(ns batik.core
  (:import (com.thinkaurelius.titan.core TitanFactory)
           (com.tinkerpop.blueprints.oupls.sail GraphSail)
           (org.openrdf.model Resource)
           (org.openrdf.model.vocabulary RDF RDFS)
           (com.tinkerpop.blueprints Vertex
                                     Edge)))


(def ^:dynamic *properties* "graph.properties")
(def ^:dynamic *graph* nil)
(def ^:dynamic *sail* nil)
(def ^:dynamic *vf* nil) 


(defn resource-str
  [prefix & more]
  (apply str (flatten [prefix (map name more)])))

(defn uri
  [value]
  (-> *vf* (.createURI value)))

(defn indv
  [base & subdirs]
  (uri (apply resource-str
         (flatten [base
                   (map #(str "/" %) subdirs)
                   "/"
                   (str (java.util.UUID/randomUUID))]))))

(defn literal
  [value & [extra]]
  (if extra
    (-> *vf* (.createLiteral value extra))
    (-> *vf* (.createLiteral value))))


(defn bnode
  [& [id]]
  (if id
    (-> *vf* (.createBNode (str id)))
    (-> *vf* .createBNode)))

(defn node
  []
  (bnode (java.util.UUID/randomUUID)))

(defn iter
  [statements]
  (reify java.util.Iterator
    (hasNext [self] (.hasNext statements))
    (next [self] (.next statements))
    (remove [self] (.remove statements))))

(defn add-statement
  [conn s p o & contexts]
  (-> conn
    (.addStatement
      s p o (into-array Resource contexts)))
  conn)

(defn remove-statements
  [conn s p o & contexts]
  (-> conn
    (.removeStatements
      s p o (into-array Resource contexts)))
  conn)

(defn get-statements
  [conn s p o inf & contexts]
  (-> conn
    (.getStatements
      s p o inf (into-array Resource contexts))
    iter
    iterator-seq))


(defn append-items
  [conn _list items & contexts]
  (when-not (empty? items)
    (apply remove-statements (concat [conn _list RDF/REST RDF/NIL] contexts))
    (loop [_list _list
           items (if (empty? (apply get-statements
                               (concat [conn _list RDF/FIRST nil false] contexts)))
                   (do (apply add-statement (concat [conn _list RDF/FIRST (first items)] contexts))
                       (rest items))
                   items)]
      (if (empty? items)
        (do
          (apply add-statement (concat [conn _list RDF/REST RDF/NIL] contexts))
          _list)
        (let [_rest (node)]
          (apply add-statement (concat [conn _list RDF/REST _rest] contexts))
          (apply add-statement (concat [conn _rest RDF/FIRST (first items)] contexts))
          (recur _rest (rest items)))))))

(defn as-vector
  [conn _list & contexts]
  (loop [_list _list ret []]
    (let [_first (->> (apply get-statements (concat [conn _list RDF/FIRST nil false] contexts))
                      (map #(-> % .getObject))
                      first)
          _rest (->> (apply get-statements (concat [conn _list RDF/REST nil false] contexts))
                     (map #(-> % .getObject))
                     first)]
      (if (= _rest RDF/NIL)
        (conj ret _first)
        (recur _rest (conj ret _first))))))

;;(defn get-items
;;  [conn _list start end & contexts]
;;  (loop [_list _list
;;         items items]
;;    (let [item (-> conn (get-statements _list RDF/FIRST nil false) first .getObject)]
;;      (
;;    (apply get-statements (concat [conn _list 


(defn query
  [conn query-string & [base-uri inf]]
  (let [parser (org.openrdf.query.parser.sparql.SPARQLParser.)
        pq (-> parser (.parseQuery query-string base-uri))]
    (-> (.evaluate conn
          (.getTupleExpr pq)
          (.getDataset pq)
          (org.openrdf.query.impl.EmptyBindingSet.)
          (or inf false))
      iter iterator-seq)))


;;SPARQLParser parser = new SPARQLParser();
;;CloseableIteration<? extends BindingSet, QueryEvaluationException> sparqlResults;
;;String queryString = "SELECT ?x ?y WHERE { ?x <http://tinkerpop.com#knows> ?y }";
;;ParsedQuery query = parser.parseQuery(queryString, "http://tinkerPop.com");
;;
;;System.out.println("\nSPARQL: " + queryString);
;;sparqlResults = sc.evaluate(query.getTupleExpr(), query.getDataset(), new EmptyBindingSet(), false);
;;while (sparqlResults.hasNext()) {
;;    System.out.println(sparqlResults.next());
;;}


(defn graph
  ([] (graph *properties*))
  ([resource-name]
    (TitanFactory/open
      (-> resource-name
          clojure.java.io/resource
          .toURI java.io.File. str))))


(defmacro with-graph
  [& forms]
  `(binding [*graph* (graph)]
    (let [ret# (do ~@forms)]
      (.shutdown *graph*)
      ret#)))


(defn- make-pk-str
  [mgmt key-name]
  (-> mgmt
    (.makePropertyKey (name key-name))
    (.dataType String)
    .make))


(defn- build-comp-index
  [mgmt pk-name index-name class-obj]
  (-> mgmt
    (.buildIndex
      (name index-name)
      class-obj)
    (.addKey (make-pk-str mgmt pk-name))
    .buildCompositeIndex))


(defn build-required-sail-indices
  []
  (with-graph
    (let [mgmt (-> *graph* .getManagementSystem)]
      (when-not (-> mgmt (.containsGraphIndex "by-value"))
        (build-comp-index mgmt :value :by-value Vertex))
      (doseq [k #{:p :c :pc}]
        (let [index-name (str "by-" (name k))]
          (when-not (-> mgmt (.containsGraphIndex index-name))
            (build-comp-index mgmt k index-name Edge))))
      (-> mgmt .commit))))


(defmacro with-sail
  [& forms]
  `(with-graph
    (binding [*sail* (-> *graph* GraphSail.)]
      (binding [*vf* (-> *sail* .getValueFactory)]
        (-> *sail* .initialize)
        (let [ret# (do ~@forms)]
          (-> *sail* .shutDown)
          ret#)))))


(defmacro with-sc
  [conn-name & forms]
  `(with-sail
     (let [~conn-name (-> *sail* .getConnection)]
       (-> ~conn-name .begin)
       (try
         (let [ret# (do ~@forms)]
           ret#)
         (catch Exception e#
           (-> e# .printStackTrace))
         (finally
           (when (-> ~conn-name .isActive)
             (-> ~conn-name .rollback))
           (when (-> ~conn-name .isOpen)
             (-> ~conn-name .close))))
       )))

