(ns appengine.datastore.test.core
  (:require [appengine.datastore.core :as ds])
  (:use clojure.test
	appengine.datastore.entities
        appengine.test-utils)
  (:import (com.google.appengine.api.datastore
            DatastoreServiceFactory
            Entity
            EntityNotFoundException
            KeyFactory
            Query
            Query$FilterOperator
            Query$SortDirection)))

(dstest test-create-key-with-int
  (let [key (ds/create-key "person" 1)]
    (is (= (class key) com.google.appengine.api.datastore.Key))
    (is (.isComplete key))
    (is (nil? (.getParent key)))    
    (is (= (.getKind key) "person"))
    (is (= (.getId key) 1))
    (is (nil? (.getName key)))))

(dstest test-create-key-with-string
  (let [key (ds/create-key "country" "de")]
    (is (= (class key) com.google.appengine.api.datastore.Key))
    (is (.isComplete key))
    (is (nil? (.getParent key)))
    (is (= (.getKind key) "country"))
    (is (= (.getId key) 0))
    (is (= (.getName key) "de"))))

(dstest test-create-key-with-parent
  (let [continent (ds/create-key "continent" "eu")
        country (ds/create-key continent "country" "de")]
    (is (= (class country) com.google.appengine.api.datastore.Key))
    (is (.isComplete country))
    (is (= (.getParent country) continent))
    (is (= (.getKind country) "country"))
    (is (= (.getId country) 0))
    (is (= (.getName country) "de"))))

(dstest test-entity->map
  (let [continent (ds/entity->map (doto (Entity. "continent") (.setProperty "name" "Europe")))]
    (let [key (:key continent)]
      (is (not (.isComplete key)))
      (is (nil? (.getParent key)))
      (is (= (.getKind key) "continent"))
      (is (= (.getId key) 0))
      (is (nil? (.getName key)))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (:kind continent) "continent"))
    (is (= (:name continent) "Europe"))))

(dstest test-entity->map-with-key
  (let [continent (ds/entity->map (doto (Entity. (ds/create-key "continent" "eu")) (.setProperty "name" "Europe")))]
    (let [key (:key continent)]
      (is (.isComplete key))
      (is (nil? (.getParent key)))
      (is (= (.getKind key) "continent"))
      (is (= (.getId key) 0))
      (is (= (.getName key) "eu"))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (:kind continent) "continent"))
    (is (= (:name continent) "Europe"))))

(dstest test-entity->map-with-parent
  (let [continent (ds/entity->map (doto (Entity. (ds/create-key "continent" "eu")) (.setProperty "name" "Europe")))
        country (ds/entity->map (doto (Entity. (ds/create-key (:key continent) "country" "es")) (.setProperty "name" "Spain")))]    
    (let [key (:key country)]
      (is (.isComplete key))
      (is (= (.getParent key) (:key continent)))
      (is (= (.getKind key) "country"))
      (is (= (.getId key) 0))
      (is (= (.getName key) "es"))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (:kind country) "country"))
    (is (= (:name country) "Spain"))))

(dstest test-entity->map-with-exisiting-entity
  (let [continent (ds/entity->map (ds/map->entity (ds/create-entity {:key (ds/create-key "continent" "eu") :name "Europe"})))]
    (let [key (:key continent)]
      (is (.isComplete key))
      (is (nil? (.getParent key)))
      (is (= (.getKind key) "continent"))
      (is (= (.getId key) 0))
      (is (= (.getName key) "eu"))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (:kind continent) "continent"))
    (is (= (:name continent) "Europe"))))

(dstest test-entity->map-with-exisiting-entity-and-parent
  (let [continent (ds/create-entity {:key (ds/create-key "continent" "eu") :name "Europe"})
        country (ds/entity->map (ds/map->entity (ds/create-entity {:key (ds/create-key (:key continent) "country" "es") :name "Spain"})))]
    (let [key (:key country)]
      ;; (is (.isComplete key))
      (is (= (.getParent key) (:key continent)))
      (is (= (.getKind key) "country"))
      (is (= (.getId key) 0))
      ;; (is (= (.getName key) "es"))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (:kind continent) "continent"))
    (is (= (:name continent) "Europe"))))

(dstest test-key->string  
  (is (= (ds/key->string (ds/create-key "person" 1)) "agR0ZXN0cgwLEgZwZXJzb24YAQw"))
  (is (= (ds/key->string (ds/create-key "country" "de")) "agR0ZXN0cg8LEgdjb3VudHJ5IgJkZQw"))
  (let [continent (ds/create-key "continent" "eu")
        country (ds/create-key continent "country" "de")]
    (is (= (ds/key->string country) "agR0ZXN0ciALEgljb250aW5lbnQiAmV1DAsSB2NvdW50cnkiAmRlDA"))))

(dstest test-string->key
  (let [key (ds/string->key "agR0ZXN0cgwLEgZwZXJzb24YAQw")]
    (is (= (.getKind key) "person"))
    (is (= (.getId key) (long 1)))
    (is (nil? (.getName key))))
  (let [key (ds/string->key "agR0ZXN0cg8LEgdjb3VudHJ5IgJkZQw")]
    (is (= (.getKind key) "country"))
    (is (= (.getId key) 0))
    (is (= (.getName key) "de")))
  (let [key (ds/string->key "agR0ZXN0ciALEgljb250aW5lbnQiAmV1DAsSB2NvdW50cnkiAmRlDA")]
    (is (= (.getKind key) "country"))
    (is (= (.getId key) 0))
    (is (= (.getName key) "de"))
    (let [parent (.getParent key)]
      (is (= (.getKind parent) "continent"))
      (is (= (.getId parent) 0))
      (is (= (.getName parent) "eu")))))

(dstest test-map->entity
  (let [entity (ds/map->entity {:key "country" :name "Germany"})]
    (is (= (class entity) Entity))
    (is (= (.getKind entity) "country"))
    (is (= (.. entity getKey getKind) "country"))
    (is (= (.. entity getKey getId) 0))
    (is (nil? (.. entity getKey getName)))
    (is (= (. entity getProperty "name") "Germany"))))

(dstest test-map->entity-with-key
  (let [entity (ds/map->entity {:key (ds/create-key "country" "de") :name "Germany"})]
    (is (= (class entity) Entity))
    (is (= (.getKind entity) "country"))
    (is (= (.. entity getKey getKind) "country"))
    (is (= (.. entity getKey getId) 0))
    (is (= (.. entity getKey getName) "de"))
    (is (= (. entity getProperty "name") "Germany"))))

(dstest test-map->entity-with-existing
  (let [continent (ds/map->entity (ds/create-entity {:key (ds/create-key "continent" "eu") :name "Europe"}))]
    (let [key (.getKey continent)]
      (is (.isComplete key))
      (is (= (.getParent key) (:key continent)))
      (is (= (.getKind key) "continent"))
      (is (= (.getId key) 0))
      (is (= (.getName key) "eu"))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (.getKind continent) "continent"))
    (is (= (. continent getProperty "name") "Europe"))))

(dstest test-map->entity-with-existing-and-parent
  (let [continent (ds/create-entity {:key (ds/create-key "continent" "eu") :name "Europe"})
        country (ds/map->entity (ds/create-entity {:key (ds/create-key (:key continent) "country" "es") :name "Spain"}))]
    (let [key (.getKey country)]
      ;; (is (.isComplete key))
      (is (= (.getParent key) (:key continent)))
      (is (= (.getKind key) "country"))
      (is (= (.getId key) 0))
      ;; (is (= (.getName key) "es"))
      (is (= (class key) com.google.appengine.api.datastore.Key)))
    (is (= (.getKind country) "country"))
    (is (= (. country getProperty "name") "Spain"))))

(dstest test-create-entity-with-struct
  (defstruct country :name :kind)
  (let [country (ds/create-entity (struct country "Germany" "country"))]
    (is (not (nil? (:key country))))
    (is (= (:name country) "Germany"))
    (is (= (:kind country) "country"))))

(dstest test-create-entity-with-int-key
  (let [person (ds/create-entity {:kind "person" :name "Bob"})]
    (is (= (class person) clojure.lang.PersistentArrayMap))
    (is (.isComplete (:key person)))
    (is (= (.getKind (:key person)) "person"))
    (is (= (.getId (:key person)) 1))
    (is (nil? (.getName (:key person))))      
    (is (= (:kind person)) "person")
    (is (= (:name person) "Bob"))))

(dstest test-create-entity-with-string-key
  (let [country (ds/create-entity {:key (ds/create-key "country" "de") :name "Germany"})]
    (is (= (class country) clojure.lang.PersistentArrayMap))
    (is (.isComplete (:key country)))
    (is (= (.getKind (:key country)) "country"))
    (is (= (.getId (:key country)) 0))
    (is (= (.getName (:key country)) "de"))    
    (is (= (:kind country)) "country")
    (is (= (:name country) "Germany"))))

(dstest test-get-entity
  (is (nil? (ds/get-entity nil)))
  (is (nil? (ds/get-entity {}))))

(dstest test-get-entity-with-int-key
  (let [person (ds/create-entity {:kind "person" :name "Bob"})]
    (is (= (class person) clojure.lang.PersistentArrayMap))
    (is (= ((ds/get-entity person) person)))
    (is (= ((ds/get-entity (:key person)) person)))
    (is (= ((ds/get-entity (ds/map->entity person)) person)))))

(dstest test-get-with-string-key
  (let [country (ds/create-entity {:key (ds/create-key "country" "de") :name "Germany"})]
    (is (= (class country) clojure.lang.PersistentArrayMap))
    (is (= ((ds/get-entity country) country)))
    (is (= ((ds/get-entity (:key country)) country)))
    (is (= ((ds/get-entity (ds/map->entity country)) country)))))

(dstest test-put-entity
  (let [continent (ds/put-entity {:name "Europe" :key (ds/create-key "continent" "eu")})]
    (is (= (count (ds/find-all (Query. "continent"))) 1))
    (let [key (:key continent)]
      (is (.isComplete key))
      (is (= (.getKind key) "continent"))
      (is (= (.getId key) 0))
      (is (= (.getName key) "eu")))
    (is (= (:name continent) "Europe"))
    (ds/put-entity continent)
    (is (= (count (ds/find-all (Query. "continent"))) 1))
    (let [country (ds/put-entity {:name "Germany" :key (ds/create-key (:key continent) "country" "de")})]
      (is (= (count (ds/find-all (Query. "country"))) 1))
      (let [key (:key country)]
        (is (.isComplete key))
        (is (= (.getParent key) (:key continent)))
        (is (= (.getKind key) "country"))
        (is (= (.getId key) 0))
        (is (= (.getName key) "de")))
      (is (= (:name country) "Germany"))
      (ds/put-entity country)
      ;; (is (= (count (ds/find-all (Query. "country"))) 1))
      )))

;; (with-local-datastore
;;   (let [continent (ds/put-entity {:name "Europe" :key (ds/create-key "continent" "eu")})
;;         country (ds/put-entity {:name "Germany" :key (ds/create-key (:key continent) "country" "de")})])
;;   ;; (println (ds/put-entity country))
;;   ;; (println (ds/put-entity country))
;;   )

(dstest delete-entity-with-key
  (let [key (:key (ds/create-entity {:kind "person" :name "Bob"}))]
    (ds/delete-entity key)
    (is (thrown? EntityNotFoundException (ds/get-entity key)))))

(dstest delete-entity-with-multiple-keys
  (let [key1 (:key (ds/create-entity {:kind "person" :name "Alice"}))
        key2 (:key (ds/create-entity {:kind "person" :name "Bob"}))]
    (ds/delete-entity [key1 key2])
    (are (thrown? EntityNotFoundException (ds/get-entity _1))
      key1 key2)))

(dstest test-update-entity
  (let [country (ds/put-entity {:key (ds/create-key "country" "de") :name "Deutschland"})]
    (let [country (ds/update-entity country {:name "Germany"})]
      (is (= (:name country) "Germany"))
      (is (= (count (ds/find-all (Query. "country"))) 1)))
    (let [country (ds/update-entity (:key country) {:name "Germany"})]
      (is (= (:name country) "Germany"))
      (is (= (count (ds/find-all (Query. "country"))) 1)))
    (let [country (ds/update-entity (ds/map->entity country) {:name "Germany"})]
      (is (= (:name country) "Germany"))
      (is (= (count (ds/find-all (Query. "country"))) 1)))))

;; (dstest test-update-entity-having-parent
;;   (let [continent (ds/put-entity {:key (ds/create-key "continent" "eu") :name "Europe"})
;;         country (ds/put-entity {:key (ds/create-key (:key continent) "country" "de") :name "Deutschland"})]
;;     (let [country (ds/update-entity country {:name "Germany"})]
;;       (is (= (:name country) "Germany"))
;;       (is (= (count (ds/find-all (Query. "country"))) 1)))
;;     ))

;; (with-local-datastore
;;   (let [continent (ds/put-entity {:key (ds/create-key "continent" "eu") :name "Europe"})
;;         country (ds/put-entity {:key (ds/create-key (:key continent) "country" "de") :name "Deutschland"})]
;;     (println country)
;;     (let [country (ds/update-entity country {:name "Germany"})]
;;       (ds/find-all (Query. "country")) 1)
;;     ))

(dstest test-properties
  (let [record {:key (ds/create-key "person" 1) :name "Bob"}]
    (is (= (ds/properties record) {:name "Bob"}))
    (is (= (ds/properties (ds/map->entity record)) {:name "Bob"}))))

(dstest entity-to-map-converts-to-persistent-map
  (let [entity (doto (Entity. "MyKind")
                 (.setProperty "foo" "Foo")
                 (.setProperty "bar" "Bar"))]
    (.put (DatastoreServiceFactory/getDatastoreService) entity)
    (is (= {:foo "Foo" :bar "Bar" :kind "MyKind" :key (.getKey entity)}
           (ds/entity->map entity)))))

(dstest find-all-runs-given-query
  (.put (DatastoreServiceFactory/getDatastoreService)
        [(doto (Entity. "A") (.setProperty "code" 1) (.setProperty "name" "jim"))
         (doto (Entity. "A") (.setProperty "code" 2) (.setProperty "name" "tim"))
         (doto (Entity. "B") (.setProperty "code" 1) (.setProperty "name" "jan"))])
  (is (= ["jim" "tim"] (map :name (ds/find-all (doto (Query. "A") (.addSort "code"))))))
  (is (= ["tim" "jim"] (map :name (ds/find-all (doto (Query. "A") (.addSort "code" Query$SortDirection/DESCENDING))))))
  (is (= ["jim"] (map :name (ds/find-all (doto (Query. "A") (.addFilter "code" Query$FilterOperator/EQUAL 1))))))
  (is (= ["jan"] (map :name (ds/find-all (doto (Query. "B") (.addFilter "code" Query$FilterOperator/EQUAL 1)))))))

(dstest create-saves-and-returns-item-with-a-key
  (let [created-item (ds/create-entity {:kind "MyKind" :name "hume" :age 31})]
    (is (not (nil? (created-item :key))))
    (let [created-entity (.get (DatastoreServiceFactory/getDatastoreService) (created-item :key))]
      (is (= "MyKind" (.getKind created-entity)))
      (is (= "hume" (.getProperty created-entity "name")))
      (is (= 31 (.getProperty created-entity "age"))))))

;; (dstest create-can-create-a-child-entity-from-a-parent-key
;;   (let [parent (ds/create-entity {:kind "Mother" :name "mama"})
;;         child (ds/create-entity {:kind "Child" :name "baby"} (parent :key))]
;;     (is (= (parent :key) (.getParent (child :key))))
;;     (is (= [child] (ds/find-all (doto (Query. "Child" (parent :key))))))))

(dstest get-given-a-key-returns-a-mapified-entity
  (let [key (:key (ds/create-entity {:kind "Person" :name "cliff"}))]
    (is (= "cliff" ((ds/get-entity key) :name)))))

;; test for :parent-key
(dstest make-entity-with-parent
  (let [parent (ds/create-entity {:kind "Person" :name "Andy" :age 31})
	child1 (ds/create-entity {:kind "Child" :name "Liz" :age 5 
                                  :parent-key (:key parent)})
	child2 (ds/create-entity {:kind "Child" :name "Jane" :age 5
                                  :parent-key (:key parent)})]
    (is (= (:parent-key child1) (:key parent)))
    (is (= (:parent-key child2) (:key parent)))))

;; test for getting multiple keys at once from ds
(dstest get-multiple-keys-from-ds
  (let [e1 (ds/create-entity {:kind "E" :name "e1" })
	e2 (ds/create-entity {:kind "E" :name "e2" })
	e3 (ds/create-entity {:kind "E" :name "e3" })
	entities (ds/get-entity (map :key [e1 e2 e3]))]
    (is (= (get entities (:key e1)) e1))
    (is (= (get entities (:key e2)) e2))
    (is (= (get entities (:key e3)) e3))
    (ds/delete-entity (map :key [e1 e2 e3]))
    (let [entities (ds/get-entity (map :key [e1 e2 e3]))]
      (is (= 0 (reduce count 0 entities))))))

(dstest update-remove-attribute
  (let [e (ds/create-entity {:kind "E" :a "a" :b "b" :c "c"})
	e-updated (ds/update-entity e {:c nil})
	e-updated2 (ds/update-entity e {:c :remove})]
    (is (contains? e :c))
    (is (contains? e-updated :c))
    (is (not (contains? e-updated2 :c)))))

(dstest delete-entity-multimethod
  (let [key (:key (ds/create-entity {:kind "E" :a "a"}))
	entity-as-map (ds/create-entity {:kind "E" :b "b"})
	entity-as-entity (ds/map->entity (ds/create-entity {:kind "E" :c "c"}))
	e1 (ds/create-entity {:kind "E" :name "e1"})
	e2 (ds/create-entity {:kind "E" :name "e2"})]
    (ds/delete-entity key)
    (ds/delete-entity entity-as-map)
    (ds/delete-entity entity-as-entity)
    (ds/delete-entity (map :key [e1 e2]))
    (are [record] (thrown? EntityNotFoundException (ds/get-entity record))
	 key entity-as-map entity-as-entity e1 e2)))

(dstest delete-entity-multimethod-with-multiple-deletes
  (let [e1 (ds/create-entity {:kind "E" :name "e1"})
	e2 (ds/create-entity {:kind "E" :name "e2"})
	e3 (ds/create-entity {:kind "E" :name "e3"})
	e4 (ds/create-entity {:kind "E" :name "e4"})
	e5 (ds/create-entity {:kind "E" :name "e5"})
	e6 (ds/create-entity {:kind "E" :name "e6"})]
    (ds/delete-entity [e1 nil e2])
    (ds/delete-entity (map :key [e3 e4 {:a :b}]))
    (ds/delete-entity (merge (map ds/map->entity [e5 e6]) {:a :b}))
    (are [record] (thrown? EntityNotFoundException (ds/get-entity record))
	 e1 e2 e3 e4 e5 e6)))
