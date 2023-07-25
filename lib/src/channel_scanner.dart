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
}