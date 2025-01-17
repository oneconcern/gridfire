(ns user
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string]
   [clojure.java.io :as io]

   [gridfire.cli :as cli]
   [gridfire.postgis-bridge :as postgis-bridge]

   [me.raynes.conch :as conch]

   [taoensso.tufte :as tufte]
   ))

(def ^:dynamic *db-config*
  {:username "gjohnson"
   :db-name "gridfire"})

(def ^:dynamic *landfire-layer-name->db-table-name*
  {:elevation "dem",
   :slope "slp",
   :aspect "asp",
   :fuel-model "fbfm40",
   :canopy-height "ch",
   :canopy-base-height "cbh",
   :crown-bulk-density "cbd",
   :canopy-cover "cc"})

(defn read-config2-file
  "the original implementation uses a config file as in `resources/sample_config.edn`.
  this function builds a configuration map of the same format as `sample_config.edn`
  by splicing in the database-specific config entries (as opposed to application-specific
  config) from the defs in this namespace."
  [path]
  (-> (edn/read-string (slurp path))
      (assoc :db-spec {:classname "org.postgresql.Driver",
                       :subprotocol "postgresql",
                       :subname (str "//localhost:5432/" (:db-name *db-config*)),
                       :user (:username *db-config*)})
      (assoc :landfire-layers
             (into {} (for [[k v] *landfire-layer-name->db-table-name*]
                        [k (str v " WHERE rid=1")])))))

(defn init-db
  [ & {:keys [srid]}]
  (let [gridfire-user (:username *db-config*)
        gridfire-db-name (:db-name *db-config*)
        to-rdr (fn [s] (java.io.StringReader. s))
        fuel-tags (vals *landfire-layer-name->db-table-name*)
        pg-user "postgres"
        srid (or srid "4326")
        init-gridfire-sql
        (string/join
         "\n"
         [(str "CREATE ROLE " gridfire-user " WITH LOGIN CREATEDB;")
          (str "CREATE DATABASE " gridfire-db-name " WITH OWNER " gridfire-user ";")
          (str "\\c " gridfire-db-name)
          "CREATE EXTENSION postgis;"])]
    (conch/with-programs [raster2pgsql psql createuser]
      (createuser "-s" pg-user)
      (psql "-U" pg-user {:in (to-rdr init-gridfire-sql)})
      (psql "-U" pg-user
            {:in (raster2pgsql "-s" srid
                               "test/input_data/tubbs_1507528800_1507530600.tif"
                               "dem")})
      (doseq [[fuel-tag-idx fuel-tag]
              (map list (map inc (range))
                   fuel-tags)]
        (psql "-U" gridfire-user "gridfire"
              {:in (raster2pgsql "-s" srid
                                 "-b" (str fuel-tag-idx)
                                 "test/input_data/lcp_tubbs_fire.lcp"
                                 fuel-tag)}))
      )))


(defn startup-health-check
  [& {:keys [skip-init db-username srid]}]
  (with-redefs [*db-config* (cond-> *db-config*
                              (some? db-username)
                              (assoc :username db-username))]
    (when (not skip-init)
      (init-db :srid srid))
    (cli/act-on-config
     (read-config2-file "test/input_data/config2.edn"))))

(comment

  (startup-health-check :db-username "gridfireuser")

  (let [[res pstats]
        (tufte/profiled
         {:dyanmic? true}
         (startup-health-check :db-username "gridfireuser" :skip-init true))]
    (tufte/format-pstats pstats))


  (tufte/add-basic-println-handler! {})

  (with-redefs [cli/*parallel-simulations* true
                cli/*profile-simulation-runs* true]
    (tufte/profile
     {:dynamic? true}
     (startup-health-check :db-username "gridfireuser" :skip-init true)))


  )

