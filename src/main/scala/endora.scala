import s7.sensation._
import s7.sensation.playlist._
import s7.sensation.artist.{Artist, Name, Terms}
import s7.sensation.song.{Search, Song, Title, Artist => SongArtist}

object Endora extends App {
  val evil = true
  val usage = "java-run-stuff [-s artist seed]* [-a artist seed]*\n  The first 5 artists/songs will be used to generate your station!"

  val keys = """Key map:
help = ?
favorite song = +
ban song = -
skip song = n
favorite artist = '
ban artist = ;
quit = q
listen = anything else
"""

  if (args.length < 2) {
    Console.println(usage)
    System.exit(-2)
  }

  implicit val apiKey = EchoNestKey(System.getenv("EN_API_KEY") match {
    case s:String => s
    case _ => Console.println(usage); System.exit(-1); ""
  })

  // Not really any error checking for command-line parsing in general
  val seeds = buildSeeds(args)
  if (seeds.length < 1) {
    Console.println(usage)
    System.exit(-2)
  }

  val list = playlist.Dynamic(seeds)
  list.steer(Variety(0))
  var description = ""
  Console.println(keys)

  val reader = System.console.reader
  while(true) {
    try {
      val (s, fb) = list.next
      Console.println(s(Title) + " - " + s(SongArtist)(Name) + " - " +
                      s(SongArtist)(Terms).take(3).map(_._1).mkString(" "))
      while (description.size == 0) {
        val input = reader.read
        if (input != 'q' && input != '?')
          Console.println(" - processing...")
        else
          Console.println()
        input match {
          case '+'  => {
            if (evil) {
              fb(BanSong)
              list.steer(PlaySimilar(-5))
            } else {
              fb(FavoriteSong)
              list.steer(PlaySimilar(5))
            }
            description = "  Made a favorite"
          }
          case '-'  => {
            if (evil) {
              fb(FavoriteSong)
              list.steer(PlaySimilar(5))
            } else {
              fb(BanSong)
              list.steer(PlaySimilar(-5))
            }
            description = "  Banned"
          }
          case 'n'  => {
            if (evil) list.steer(PlaySimilar(1));
            else {
              fb(SkipSong);
              list.steer(PlaySimilar(-1))
            }
            description = "  Skipped"
          }

          // Reseed the playlist strongly with styles resembling those of the artist
          case '\'' => {
            if (evil) {
              fb(BanArtist)
              list.restart(Song.search(Search.Style(
                s(SongArtist)(Terms).map(_._1).takeRight(3))))
            } else {
              fb(FavoriteArtist)
              list.restart(Song.search(Search.Style(
                s(SongArtist)(Terms).map(_._1).take(3))))
            }
            description = "  Made the artist a favorite"
          }
          case ';'  => {
            if (evil) {
              fb(FavoriteArtist)
              list.restart(Song.search(Search.Style(
                s(SongArtist)(Terms).map(_._1).take(3))))
            } else {
              fb(BanArtist)
              list.restart(Song.search(Search.Style(
                s(SongArtist)(Terms).map(_._1).takeRight(3))))
            }
            description = "  Banned the artist"
          }
          case 'q'  => list.delete; System.exit(0)
          case '?'  => Console.println(keys)
          case _    => {
            if (evil) {
              fb(SkipSong);
              list.steer(PlaySimilar(-1))
            } else
              list.steer(PlaySimilar(1));
            description = "  Listened"
          }
        }
      }
      Console.println(description)
      description = ""
    } catch {
      // Needs to get feedback or be steered or the list will just repeat
      case ex: NoSuchElementException =>
        list.restart(seeds)
    }
  }

  def buildSeeds(arr: Array[String]): List[PlaylistSeed] = {
    val cond = (x:String) => x != "-a" && x != "-s"
    val (cur, next) = arr.tail.span(cond)
    (arr.head match {
      case "-a" => Artist(Name -> cur.mkString(" "))
      case _ => Song.search(Search.Title(cur.mkString(" "))).head
    }) :: (if (next.length > 1) buildSeeds(next)
           else List.empty[PlaylistSeed])
  }
}
