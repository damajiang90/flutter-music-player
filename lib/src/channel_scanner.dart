import 'dart:async';

import 'package:flutter/services.dart';

class LocalMusicScanner {
  static const MethodChannel _channel = MethodChannel('tech.soit.quiet/player.scan');
  
  static Future<List<dynamic>> scanLocalSongs() async {
    var completer = Completer<List<dynamic>>();
    List<dynamic> songs = await _channel.invokeMethod('scanLocalSongs');
    completer.complete(songs);
    return completer.future;
  }

  static Future<Uint8List> loadContentThumbnail(Uri uri) async {
    var arguments = <String, String> {
      "uri": uri.toString()
    };
    var completer = Completer<Uint8List>();
    Uint8List thumbnail = await _channel.invokeMethod('loadContentThumbnail', arguments);
    completer.complete(thumbnail);
    return completer.future;
  }
}