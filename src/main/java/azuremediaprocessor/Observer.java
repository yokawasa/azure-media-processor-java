package azuremediaprocessor;

import azuremediaprocessor.State;

public interface Observer {
    public void notify(State state);
}
