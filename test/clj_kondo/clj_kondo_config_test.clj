(ns clj-kondo.clj-kondo-config-test
  (:require
   [clj-kondo.impl.version :as version]
   [clj-kondo.test-utils :refer [lint! assert-submaps assert-submaps2]]
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is]])
  (:import 
   java.time.format.DateTimeFormatter
   java.time.LocalDate))

(deftest unexpected-linter-name-test
  (testing "Unexpected linter name"
    (assert-submaps
     '({:file ".clj-kondo/config.edn", :row 1, :col 12, :level :warning, :message "Unexpected linter name: :foo"})
     (lint! "{:linters {:foo 1}}" "--filename" ".clj-kondo/config.edn")))
  (testing "Linter config should go under :linters"
    (assert-submaps
     '({:file ".clj-kondo/config.edn", :row 1, :col 2, :level :warning, :message "Linter config should go under :linters"})
     (lint! "{:unresolved-symbol {}}" "--filename" ".clj-kondo/config.edn"))))

(deftest should-be-map-test
  (testing "Top level maps"
    (assert-submaps
     '({:file ".clj-kondo/config.edn", :row 1, :col 11, :level :warning, :message "Expected a map, but got: int"})
     (lint! "{:linters 1}" "--filename" ".clj-kondo/config.edn")))
  (testing "Linter config"
    (assert-submaps
     '({:file ".clj-kondo/config.edn", :row 1, :col 28, :level :warning, :message "Expected a map, but got: int"})
     (lint! "{:linters {:unused-binding 1}}" "--filename" ".clj-kondo/config.edn"))))

(deftest qualified-symbol-test
  (testing "Top level maps"
    (assert-submaps2
     '({:file "<stdin>", :row 1, :col 1, :level :error, :message "Unresolved symbol: x"})
     (lint! "x" '{:linters {:unresolved-symbol {:exclude [(foo.bar)]
                                                    :level :error}}}))))

(defn ^:private version-with-altered-date
  "Extracts the date part of the version, applies the date-changer
   to it (expects LocalDate argument) and returns the result
   as a version string"
  [date-changer]
    (let [date-part (first (str/split
                          version/version
                          #"\-"))
        date (LocalDate/parse
              date-part
              (DateTimeFormatter/ofPattern
               "yyyy.MM.dd"))
        plus-one-day (date-changer date)]
    (.format
     plus-one-day
     (DateTimeFormatter/ofPattern
      "yyyy.MM.dd"))))

(defn ^:private one-day-in-future
  "Returns a version one day in the future from the current version"
  []
  (version-with-altered-date
   #(.plusDays % 1)))

(defn ^:private one-day-in-past
  "Returns a version one day in the past from the current version"
  []
  (version-with-altered-date
   #(.plusDays % -1)))

(deftest minimum-version-test
  (testing "No finding when version equal to minimum"
    (let [findings (lint!
                    (str
                     "{:min-clj-kondo-version \""
                     version/version
                     "\"}")
                    '{:linters {:unresolved-symbol {:exclude [(foo.bar)]
                                                    :level :error}}
                      :min-clj-kondo-version version/version}
                    "--filename"
                    ".clj-kondo/config.edn")]
      (is (empty? findings)))) 
  (testing "No finding when version after minimum"
    (let [findings (lint!
                    (str
                     "{:min-clj-kondo-version \""
                     (one-day-in-past)
                     "\"}")
                    '{:linters {:unresolved-symbol {:exclude [(foo.bar)]
                                                    :level :error}}
                      :min-clj-kondo-version (one-day-in-past)}
                    "--filename"
                    ".clj-kondo/config.edn")]
      (is (empty? findings))))
  (testing "Find when version before minimum"
    (let [findings (lint!
                    (str
                     "{:min-clj-kondo-version \""
                     (one-day-in-future)
                     "\"}")
                    '{:linters {:unresolved-symbol {:exclude [(foo.bar)]
                                                    :level :error}}
                      :min-clj-kondo-version (one-day-in-future)}
                    "--filename"
                    ".clj-kondo/config.edn")
          message (:message (first findings))]
      (assert-submaps2
       '({:file ".clj-kondo/config.edn"
          :row 1
          :col 2
          :level :warning})
       findings)
      (is
       (str/includes?
        message
        "Version"))
      (is
       (str/includes?
        message
        "below configured minimum")))))
