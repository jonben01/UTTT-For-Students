package dk.easv.bll.bot;

import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;

import java.util.List;
import java.util.Random;

public class DavidBot implements IBot {
    private final Random random = new Random();

    @Override
    public IMove doMove(IGameState state) {
        List<IMove> availableMoves = state.getField().getAvailableMoves();

        if (availableMoves.isEmpty()) {
            return null;
        }

        // First check for a winning move
        for (IMove move : availableMoves) {
            if (isWinningMove(state, move)) {
                return move;
            }
        }

        // Then block opponent's winning move
        for (IMove move : availableMoves) {
            if (isOpponentWinningMove(state, move)) {
                return move;
            }
        }

        // Then prioritize center > corners > edges
        for (IMove move : availableMoves) {
            if (isPreferredMove(move)) {
                return move;
            }
        }

        // Random fallback
        return availableMoves.get(random.nextInt(availableMoves.size()));
    }

    private boolean isWinningMove(IGameState state, IMove move) {
        return false;
    }

    private boolean isOpponentWinningMove(IGameState state, IMove move) {
        return false;
    }

    private boolean isPreferredMove(IMove move) {
        // Define clearer preference logic: center > corners > edges
        int x = move.getX();
        int y = move.getY();

        // Center
        if (x == 1 && y == 1) {
            return true;
        }

        // Corners
        if ((x == 0 || x == 2) && (y == 0 || y == 2)) {
            return true;
        }

        // Edges
        if ((x == 1 && (y == 0 || y == 2)) || (y == 1 && (x == 0 || x == 2))) {
            return true;
        }

        return false;
    }

    @Override
    public String getBotName() {
        return "DavidBot";
    }
}
