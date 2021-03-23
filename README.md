# tech-days-lia-game

## Getting started

1. Fork the project for team collaboration. You are responsible for keeping your team's secrets secret!
2. Share the forked project to event organizers, so that they have access to the ai for show matches and finals.
3. Options for coding language are python, java and [kotlin](https://kotlinlang.org/).
4. Windows and macos sdk have been tested to be working and are included in this project. [Original project in github and sdk for linux](https://github.com/planet-lia/lia-SDK/releases/tag/v1.0.2)
5. To create a bot/ai with your choice of language, run the following command at sdk root

windows cmd `lia.exe bot java John`
powershell `.\lia.exe bot python3 John`
macos/linux `./lia bot kotlin John`

6. To run a match with ai named John against itself, run the following command

windows `lia.exe play John John`
powrshell `.\lia.exe play John John`
macos/linux `./lia play John John`

7. A replay of the match should open in its' own window and be saved under `./replays`.
8. Ai's written with different languages or sdk can be matched against each other.
9. [API reference](https://docs.liagame.com/api/) of available functions and gamestate parameters
10. [Strategy ideas](https://docs.liagame.com/strategy-ideas/)

## Rules

1. Official rules and game mechanics are described on https://docs.liagame.com/game-rules. We are not using any rules customizations.
2. Publish the ai that you wish to put against opposing teams at the root level of the repository. Organizer will use that ai for scheduled matches.
3. Winner team is decided by playing every team's ai against each other. The ai that wins the most in three cycles of round robin scheduled at the end of the event is the winner. If there is a tie, one more round is run between tied teams, and most surviving units are used as a tie-breaker if there still a tie among three or more teams.

## Troubleshoot

### When trying to create a python ai, the command throws an error about missing executable python file

Run the following command in the sdk root: `virtualenv --python C:/{path to locally installed python}/python.exe venv`

### When trying run a match with a python ai, the command results in a loop with an error message about non-iterable locks

In file bot/venv/Lib/site-package/websockets/protocol.py starting on line 736 there should be the following code block
```
        try:
            # drain() cannot be called concurrently by multiple coroutines:
            # http://bugs.python.org/issue29930. Remove this lock when no
            # version of Python where this bugs exists is supported anymore.
            with (yield from self._drain_lock):
                # Handle flow control automatically.
                yield from self.writer.drain()
        except ConnectionError:
            # Terminate the connection if the socket died.
            self.fail_connection()
            # Wait until the connection is closed to raise ConnectionClosed
            # with the correct code and reason.
            yield from self.ensure_open()
```

This code block can be removed when using newer versions of python and the match will run without issues.

### My windows-sdk python ai cannot be matched against macos-sdk python ai because of a missing file error

Replace the contents of the `./{ai name}/venv` of the non-native ai to match the contents of the same directory of the ai native to your operating system.
