package models

sealed trait WatcherStatus

object WatcherStatus {
  case object Success extends WatcherStatus
  case class Failure(reason: String) extends WatcherStatus
}