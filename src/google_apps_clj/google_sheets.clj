(ns google-apps-clj.google-sheets
  (:require [clojure.core.typed :as t]
            [clojure.edn :as edn :only [read-string]]
            [clojure.java.io :as io :only [as-url file resource]]
            [google-apps-clj.credentials :as cred])
  (:import (com.google.gdata.data.spreadsheet CellEntry
                                              CellFeed
                                              ListEntry
                                              ListFeed
                                              SpreadsheetEntry
                                              SpreadsheetFeed
                                              WorksheetEntry
                                              WorksheetFeed)
           (com.google.gdata.data ILink$Rel
                                  ILink$Type
                                  PlainTextConstruct)
           (com.google.gdata.client.spreadsheet CellQuery
                                                SpreadsheetQuery
                                                SpreadsheetService
                                                WorksheetQuery)
           (com.google.gdata.data.batch BatchOperationType
                                        BatchUtils)))
(t/ann ^:no-check clojure.java.io/as-url [t/Str -> java.net.URL])

(def spreadsheet-url
  "The url needed and used to recieve a spreadsheet feed"
  (io/as-url "https://spreadsheets.google.com/feeds/spreadsheets/private/full"))

(t/ann build-sheet-service [cred/GoogleCtx -> SpreadsheetService])
(defn build-sheet-service
  "Given a google-ctx configuration map, builds a SpreadsheetService using
   the credentials coming from google-ctx"
  [google-ctx]
  (let [google-credential (cred/build-credential google-ctx)
        service (doto (SpreadsheetService. "Default Spreadsheet Service")
                  (.setOAuth2Credentials google-credential))]
    service))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;   Worksheet Entry Functions  ;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(t/ann create-new-worksheet
       [cred/GoogleCtx SpreadsheetEntry Number Number String -> WorksheetEntry])
(defn create-new-worksheet
  "Given a google-ctx configuration map, SpreadsheetEntry, rows, columns, and 
   a title, create a new worksheet for for the SpreadsheetEntry with this data"
  [google-ctx spreadsheet-entry rows cols title]
  (let [sheet-service (build-sheet-service google-ctx)
        worksheet (doto (WorksheetEntry.)
                    (.setTitle (PlainTextConstruct. title))
                    (.setRowCount (int rows))
                    (.setColCount (int cols)))
        feed-url (doto (.getWorksheetFeedUrl ^SpreadsheetEntry spreadsheet-entry)
                   assert)]
    (cast WorksheetEntry (doto (.insert ^SpreadsheetService sheet-service feed-url worksheet)
                           assert))))

(t/ann update-worksheet-row-count [WorksheetEntry Number -> WorksheetEntry])
(defn update-worksheet-row-count
  "Given a WorksheetEntry and desired amount of rows, edit and
   return the new WorkSheetEntry"
  [worksheet-entry rows]
  (let [worksheet (doto ^WorksheetEntry worksheet-entry
                        (.setRowCount (int rows)))]
    (cast WorksheetEntry (doto (.update worksheet)
                           assert))))

(t/ann update-worksheet-col-count [WorksheetEntry Number -> WorksheetEntry])
(defn update-worksheet-col-count
  "Given a WorksheetEntry and desired amount of columns, edit and
   return the new WorkSheetEntry"
  [worksheet-entry cols]
  (let [worksheet (doto ^WorksheetEntry worksheet-entry
                        (.setColCount (int cols)))]
    (cast WorksheetEntry (doto (.update worksheet)
                           assert))))

(t/ann update-worksheet-all-fields [WorksheetEntry Number Number String -> WorksheetEntry])
(defn update-worksheet-all-fields
  "Update all the fields for the given worksheet and return the new worksheet"
  [worksheet-entry rows cols title]
  (let [worksheet (doto ^WorksheetEntry worksheet-entry
                        (.setRowCount (int rows))
                        (.setColCount (int cols))
                        (.setTitle (PlainTextConstruct. title)))]
    (cast WorksheetEntry (doto (.update worksheet)
                           assert))))

(t/ann ^:no-check find-worksheet-by-id
       [SpreadsheetService SpreadsheetEntry String -> (t/U '{:worksheet WorksheetEntry}
                                                           '{:error (t/Val :no-entry)})])
(defn find-worksheet-by-id
  "Given a SpreadsheetService, SpreadSheetEntry and the id of a worksheet, find 
   the WorksheetEntry with the given id in a map, or an error message in a map"
  [sheet-service spreadsheet id]
  (let [url (io/as-url (str (.getWorksheetFeedUrl ^SpreadsheetEntry spreadsheet) "/" id))
        entry (.getEntry sheet-service url WorksheetEntry nil)]
    (if entry
      {:worksheet entry}
      {:error :no-entry})))

(t/ann ^:no-check find-worksheet-by-title
       [SpreadsheetService SpreadsheetEntry String -> (t/U '{:worksheet WorksheetEntry}
                                                           '{:error t/Keyword})])
(defn find-worksheet-by-title
  "Given a SpreadsheetService, SpreadSheetEntry and a title of a worksheet, find the WorksheetEntry 
   with the given title in a map, or an error message in a map"
  [sheet-service spreadsheet title]
  (let [query (doto (WorksheetQuery. (.getWorksheetFeedUrl ^SpreadsheetEntry spreadsheet))
                (.setTitleQuery title)
                (.setTitleExact true))
        unverified (-> sheet-service (.query query WorksheetFeed) .getEntries)]
    (cond (= 1 (count unverified)) {:worksheet (first unverified)}
          (< (count unverified) 1) {:error :no-worksheet}
          (> (count unverified) 1) {:error :more-than-one-worksheet})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;; Spreadsheet Entry Functions ;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn find-spreadsheet-by-id
;;   "Given a SpreadsheetService and the id of a spreadsheet, find the SpreadsheetEntry
;;    with the given title in a map, or an error message in a map"
;;   [sheet-service id]
;;   (let [sheet-url (io/as-url (str spreadsheet-url "/" id))
;;         entry (.getEntry sheet-service sheet-url SpreadsheetEntry nil)]
;;     (if entry
;;       {:spreadsheet entry}
;;       {:error :no-entry})))

;; (defn find-spreadsheet-by-title
;;   "Given a SpreadsheetService and a title of a spreadsheet, find the SpreadsheetEntry
;;    with the given title in a map, or an error message in a map"
;;   [sheet-service title]
;;   (let [query (doto (SpreadsheetQuery. spreadsheet-url)
;;                 (.setTitleQuery title)
;;                 (.setTitleExact true))
;;         entries (-> sheet-service (.query query SpreadsheetFeed) .getEntries)]
;;     (cond (= 1 (count entries)) {:spreadsheet (first entries)}
;;           (< (count entries) 1) {:error :no-spreadsheet}
;;           (> (count entries) 1) {:error :more-than-one-spreadsheet})))

;; (defn convert-gid-to-id
;;   "This is really hacky and probably will break. Seriously, use this with caution,
;;    I have no idea if this will continue to work if Google changes the way they 
;;    create the id from the g-id. If something breaks, check this first"
;;   [g-id]
;;   (let [g-id (Integer. g-id)
;;         new-style? (> g-id 31578)
;;         xor-value (if new-style? 474 31578)
;;         id (if new-style? (str "o" (Integer/toString (bit-xor g-id xor-value) 36))
;;                (str (Integer/toString (bit-xor g-id xor-value) 36)))]
;;     id))

;; (defn file-name->ids
;;   "Given a google-ctx, and spreadsheet name, gets the spreadsheet id and all of the 
;;    worksheet ids for this file and outputs them as a map"
;;   [google-ctx spreadsheet-name]
;;   (let [sheet-service (build-sheet-service google-ctx)
;;         spreadsheet (find-spreadsheet-by-title sheet-service spreadsheet-name)]
;;     (if (contains? spreadsheet :error)
;;       spreadsheet
;;       (let [spreadsheet-id (.getId (:spreadsheet spreadsheet))
;;             spreadsheet-id (subs spreadsheet-id (inc (.lastIndexOf spreadsheet-id "/")))
;;             worksheets (seq (.getWorksheets (:spreadsheet spreadsheet)))
;;             get-id (fn [worksheet-entry]
;;                      (let [worksheet-id (.getId worksheet-entry)]
;;                        [(subs worksheet-id (inc (.lastIndexOf worksheet-id "/")))
;;                         (.getPlainText (.getTitle worksheet-entry))]))
;;             all-worksheets (map get-id worksheets)
;;             worksheet-map (into {} all-worksheets)]
;;         {:spreadsheet {spreadsheet-id spreadsheet-name}
;;          :worksheets worksheet-map}))))

;; (defn find-cell-by-row-col
;;   "Given a SpreadsheetService, a WorksheetEntry, a row and a column,
;;    return the CellEntry at that location in a map, or an error message in a map"
;;   [sheet-service worksheet row col]
;;   (let [row (int row)
;;         col (int col)
;;         cell-feed-url (.getCellFeedUrl worksheet)
;;         cell-query (doto (CellQuery. cell-feed-url)
;;                      (.setReturnEmpty true)
;;                      (.setMinimumRow row)
;;                      (.setMaximumRow row)
;;                      (.setMinimumCol col)
;;                      (.setMaximumCol col))
;;         cells (-> (.query sheet-service cell-query CellFeed)
;;                   .getEntries)]
;;     (cond (= 1 (count cells)) {:cell (first cells)}
;;           (< (count cells) 1) {:error :no-cells}
;;           (> (count cells) 1) {:error :more-than-one-cell})))

;; (defn update-cell!
;;   "Given a google-ctx configuration map, the id of a spreadsheet,
;;    id of a worksheet in that spreadsheet, and a cell(in form [row col value],
;;    changes the value in the cell location inside of the given
;;    worksheet inside of the spreadsheet, or returns an error map"
;;   [google-ctx spreadsheet-id worksheet-id [row col value]]
;;   (let [sheet-service (build-sheet-service google-ctx)
;;         spreadsheet (find-spreadsheet-by-id sheet-service spreadsheet-id)
;;         worksheet (if (contains? spreadsheet :error)
;;                     spreadsheet
;;                     (find-worksheet-by-id sheet-service (:spreadsheet spreadsheet) worksheet-id))
;;         cell-feed-url (if (contains? worksheet :error)
;;                         worksheet
;;                         {:cell-feed-url (.getCellFeedUrl (:worksheet worksheet))})]
;;     (if (contains? cell-feed-url :error)
;;       cell-feed-url
;;       (let [cell-feed-url (:cell-feed-url cell-feed-url)
;;             cell-id (str "R" row "C" col)
;;             cell-url (io/as-url (str cell-feed-url "/" cell-id))
;;             cell (doto (CellEntry. row col value)
;;                    (.setId (str cell-url)))
;;             _ (.setHeader sheet-service "If-Match" "*") ]
;;         (.update sheet-service cell-url cell)))))

;; (defn insert-row!
;;   "Given a google-ctx configuration map, the name of a spreadsheet, 
;;    name of a worksheet in that spreadsheet, and a map of header-value pairs
;;    ({header value}) where header and value are both strings.
;;    NOTE: The headers must be all lowercase with no capital letters even if the header
;;    in the sheet has either one of those properties
;;    NOTE: headers are the values in the first row of a Google Spreadsheet"
;;   [google-ctx spreadsheet-id worksheet-id row-values]
;;   (let [sheet-service (build-sheet-service google-ctx)
;;         spreadsheet (find-spreadsheet-by-id sheet-service spreadsheet-id)
;;         worksheet (if (contains? spreadsheet :error)
;;                     spreadsheet
;;                     (find-worksheet-by-id sheet-service (:spreadsheet spreadsheet) worksheet-id))
;;         list-feed-url (if (contains? worksheet :error)
;;                         worksheet
;;                         {:list-feed-url (.getListFeedUrl (:worksheet worksheet))})
;;         list-feed (if (contains? list-feed-url :error)
;;                     list-feed-url
;;                     {:list-feed (.getFeed sheet-service (:list-feed-url list-feed-url) ListFeed)})
;;         row (ListEntry.)
;;         headers (keys row-values)
;;         update-value-by-header (fn [header]
;;                                    (.setValueLocal (.getCustomElements row)
;;                                                    header (get row-values header)))]
;;     (if (contains? list-feed :error)
;;       list-feed
;;       (do (dorun (map update-value-by-header headers))
;;           (.insert sheet-service (:list-feed-url list-feed-url) row)))))

;; (defn batch-update-cells!
;;   "Given a google-ctx configuration map, the id of a spreadsheet, the id of
;;    a worksheet, and a list of cells(in the form [row column value]), sends a batch
;;    request of all cell updates to the drive api. Will return {:error :msg} if 
;;    something goes wrong along the way"
;;   [google-ctx spreadsheet-id worksheet-id cells]
;;   (let [sheet-service (build-sheet-service google-ctx)
;;         spreadsheet (find-spreadsheet-by-id sheet-service spreadsheet-id)
;;         worksheet (if (contains? spreadsheet :error)
;;                     spreadsheet
;;                     (find-worksheet-by-id sheet-service (:spreadsheet spreadsheet) worksheet-id))
;;         cell-feed-url (if (contains? worksheet :error)
;;                         worksheet
;;                         {:cell-feed-url (.getCellFeedUrl (:worksheet worksheet))})]
;;     (if (contains? cell-feed-url :error)
;;       cell-feed-url
;;       (let [cell-feed-url (:cell-feed-url cell-feed-url)
;;             batch-request (CellFeed.)
;;             create-update-entry (fn [[row col value]]
;;                                   (let [batch-id (str "R" row "C" col)
;;                                         entry-url (io/as-url (str cell-feed-url "/" batch-id))
;;                                         entry (doto (CellEntry. row col value)
;;                                                 (.setId (str entry-url))
;;                                                 (BatchUtils/setBatchId batch-id)
;;                                                 (BatchUtils/setBatchOperationType BatchOperationType/UPDATE))]
;;                                     (-> batch-request .getEntries (.add entry))))
;;             update-requests (doall (map create-update-entry cells))
;;             cell-feed (.getFeed sheet-service cell-feed-url CellFeed)
;;             batch-link (.getLink cell-feed ILink$Rel/FEED_BATCH ILink$Type/ATOM)
;;             batch-url (io/as-url (.getHref batch-link))
;;             _ (.setHeader sheet-service "If-Match" "*")] 
;;         (.batch sheet-service batch-url batch-request)))))

;; (defn read-worksheet-headers
;;   "Given a Spreadsheet Service and a WorksheetEntry, return
;;    the value of all header cells(the first row in a worksheet)"
;;   [sheet-service worksheet-entry]
;;   (let [max-col (.getColCount worksheet-entry)
;;         cell-feed-url (.getCellFeedUrl worksheet-entry)
;;         cell-query (doto (CellQuery. cell-feed-url)
;;                      (.setReturnEmpty true)
;;                      (.setMinimumRow (int 1))
;;                      (.setMaximumRow (int 1))
;;                      (.setMinimumCol (int 1))
;;                      (.setMaximumCol max-col))
;;         cells (-> (.query sheet-service cell-query CellFeed)
;;                   .getEntries)
;;         get-value (fn [cell]
;;                     (let [value (.getValue (.getCell cell))]
;;                       (if (string? value) value "")))]
;;     (into [] (map get-value cells))))

;; (defn read-worksheet-values
;;   "Given a SpreadsheetService, and a WorksheetEntry, reads in that worksheet and returns
;;    the data from the cells as a list of vectors of strings '(['example']). Will return
;;    {:error :msg} if something goes wrong along the way such as a missing worksheet "
;;   [sheet-service worksheet-entry]
;;   (let [list-feed (.getFeed sheet-service (.getListFeedUrl worksheet-entry) ListFeed)
;;         entries (.getEntries list-feed)
;;         get-value (fn [row tag]
;;                     (let [value (.getValue row tag)]
;;                       (if (string? value) value "")))
;;         print-value (fn [entry]
;;                       (let [row (.getCustomElements entry)]
;;                         (into [] (map #(get-value row %) (.getTags row)))))]
;;     (map print-value entries)))

;; (defn read-worksheet
;;   "Given a google-ctx configuration map, the id of a spreadsheet, the id of
;;    a worksheet, reads in the worksheet as a list of vectors of strings, and
;;    seperates the headers and values of the sheet (first row and all other rows)"
;;   [google-ctx spreadsheet-id worksheet-id]
;;   (let [sheet-service (build-sheet-service google-ctx)
;;         spreadsheet (find-spreadsheet-by-id sheet-service spreadsheet-id)
;;         worksheet (if (contains? spreadsheet :error)
;;                     spreadsheet
;;                     (find-worksheet-by-id sheet-service (:spreadsheet spreadsheet) worksheet-id))]
;;     (if (contains? worksheet :error)
;;       worksheet
;;       (let [headers (read-worksheet-headers sheet-service (:worksheet worksheet))
;;             values (read-worksheet-values sheet-service (:worksheet worksheet))]
;;         {:headers headers :values values}))))

;; (defn write-worksheet
;;   "Given a google-ctx configuration map, the id of a spreadsheet, the id of a worksheet,
;;    and a map of the data {:headers data :values data}, resizes the sheet, which erases all
;;    of the previous data, creates cells for the new data-map and calls batch-update cells 
;;    on the data in chunks of a certain size that the API can handle"
;;   [google-ctx spreadsheet-id worksheet-id data-map]
;;   (let [sheet-service (build-sheet-service google-ctx)
;;         spreadsheet (find-spreadsheet-by-id sheet-service spreadsheet-id)
;;         worksheet (if (contains? spreadsheet :error)
;;                     spreadsheet
;;                     (find-worksheet-by-id sheet-service (:spreadsheet spreadsheet) worksheet-id))]
;;     (if (contains? worksheet :error)
;;       worksheet
;;       (let [headers (:headers data-map)
;;             values (:values data-map)
;;             rows-needed (inc (count values))
;;             cols-needed (apply max (cons (count headers) (map count values)))
;;             worksheet-name (.getPlainText (.getTitle (:worksheet worksheet)))
;;             worksheet (update-worksheet-all-fields (:worksheet worksheet) 1 1 worksheet-name)
;;             _ (update-cell! google-ctx spreadsheet-id worksheet-id [1 1 ""])
;;             worksheet (update-worksheet-all-fields worksheet rows-needed cols-needed worksheet-name)
;;             build-cell (fn [column value]
;;                          [(inc column) value])
;;             build-row (fn [row-number row]
;;                         (map #(into [] (cons row-number %)) (map-indexed build-cell row)))
;;             header-cells (map #(vector (inc (first %)) (second %) (nth % 2))
;;                               (apply concat (map-indexed build-row (list headers))))
;;             value-cells (map #(vector (+ 2 (first %)) (second %) (nth % 2))
;;                              (apply concat (map-indexed build-row values)))
;;             all-cells (partition-all 10000 value-cells)
;;             all-cells (cons (concat header-cells (first all-cells)) (rest all-cells))]
;;         (dorun (map #(batch-update-cells! google-ctx spreadsheet-id worksheet-id %) all-cells))))))

;; #_;;Example Usage
;; (defn example-update-data
;;   "This is an example of uploading a tsv to drive, taking the spreadsheet
;;    id that is returned, and using that as an IMPORTRANGE in another spreadsheet"
;;   [google-ctx file parent-folder-id file-title file-description
;;    media-type spreadsheet-id worksheet-id]
;;   (let [upload-file-metadata (upload-file! google-ctx file parent-folder-id file-title file-description media-type)
;;         spreadsheet-id (get upload-file-metadata "id")
;;         ;;NOTE: We are using structure of IMPORTRANGE("sheet-id", "worksheet-name!A1:C10") to import from inserted sheet
;;         value (str "=IMPORTRANGE(\"" spreadsheet-id "\", \"Sheet1!A1:H200\")")]
;;     (update-cell! google-ctx spreadsheet-id worksheet-id [1 1 value])))
